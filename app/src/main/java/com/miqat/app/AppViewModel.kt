package com.miqat.app

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miqat.app.core.Geo
import com.miqat.app.core.Prefs
import com.miqat.app.core.SavedLocation
import com.miqat.app.data.MiqatDb
import com.miqat.app.data.Mosque
import com.miqat.app.data.MosqueRepository
import com.miqat.app.hijri.CalendarMethod
import com.miqat.app.timetable.MawaqitSource
import com.miqat.app.timetable.TimetableBundle
import com.miqat.app.timetable.TimetableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LatLon(val lat: Double, val lon: Double)

sealed interface TtState {
    data object Loading : TtState
    data class Loaded(val bundle: TimetableBundle) : TtState
}

data class UiState(
    val point: LatLon? = null,
    val manual: Boolean = false,
    val placeName: String? = null,
    val permissionGranted: Boolean = false,
    val mosques: List<Mosque> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val radiusKm: Int = 10,
    val stale: Boolean = false,
    val fetchedAt: Long = 0L,
    val error: String? = null,
    val expandedId: String? = null,
    val timetables: Map<String, TtState> = emptyMap(),
    val calendarMethod: CalendarMethod = CalendarMethod.UMM_AL_QURA,
    val dayAdjustment: Int = 0
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val db = MiqatDb.build(app)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "MIQAT/1.0 (Android)").build())
        }
        .build()
    private val mosqueRepo = MosqueRepository(db.cache(), http)
    private val timetableRepo = TimetableRepository(db.cache(), listOf(MawaqitSource(http)))

    private val _state = MutableStateFlow(
        UiState(calendarMethod = prefs.calendarMethod, dayAdjustment = prefs.dayAdjustment)
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    var askedForPermission = false
    var permissionDenials = 0

    private var rawMosques: List<Mosque> = emptyList()
    private var searchOrigin: LatLon? = null
    private var geocodeOrigin: LatLon? = null
    private var searchJob: Job? = null

    init {
        prefs.loadLocation()?.let { s ->
            _state.update { it.copy(point = LatLon(s.lat, s.lon), manual = s.manual) }
            startSearch()
            resolvePlace()
        }
    }

    fun setPermission(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
    }

    /** Called with each GPS fix while the app is visible. Ignored while a manual location is chosen. */
    fun onLocation(lat: Double, lon: Double) {
        if (_state.value.manual) return
        applyPoint(LatLon(lat, lon), manual = false)
    }

    fun setManualLocation(lat: Double, lon: Double) = applyPoint(LatLon(lat, lon), manual = true)

    fun useGps() {
        val p = _state.value.point
        _state.update { it.copy(manual = false) }
        if (p != null) prefs.saveLocation(SavedLocation(p.lat, p.lon, false))
    }

    private fun applyPoint(p: LatLon, manual: Boolean) {
        prefs.saveLocation(SavedLocation(p.lat, p.lon, manual))
        _state.update { it.copy(point = p, manual = manual) }
        recomputeDistances()
        val moved = searchOrigin?.let { Geo.distanceMeters(it.lat, it.lon, p.lat, p.lon) > 1000 } ?: true
        if (moved) startSearch()
        resolvePlace()
    }

    private fun recomputeDistances() {
        val s = _state.value
        val p = s.point ?: return
        val list = rawMosques
            .map { it.copy(distanceM = Geo.distanceMeters(p.lat, p.lon, it.lat, it.lon)) }
            .filter { it.distanceM <= s.radiusKm * 1000 }
            .sortedBy { it.distanceM }
        _state.update { it.copy(mosques = list) }
    }

    fun startSearch() {
        val s = _state.value
        val p = s.point ?: return
        searchOrigin = p
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val r = mosqueRepo.search(p.lat, p.lon, s.radiusKm)
                rawMosques = r.mosques
                _state.update { it.copy(stale = r.stale, fetchedAt = r.fetchedAt, loading = false, searched = true) }
                recomputeDistances()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, searched = true,
                        error = "Could not load nearby mosques. Check your internet connection and try again.")
                }
            }
        }
    }

    fun widenSearch() {
        val next = when (_state.value.radiusKm) { 10 -> 25; 25 -> 50; else -> 50 }
        _state.update { it.copy(radiusKm = next) }
        startSearch()
    }

    fun toggleMosque(id: String) {
        val cur = _state.value
        if (cur.expandedId == id) {
            _state.update { it.copy(expandedId = null) }
            return
        }
        _state.update { it.copy(expandedId = id) }
        val loaded = cur.timetables[id] as? TtState.Loaded
        if (loaded == null || loaded.bundle.date != LocalDate.now().toString()) loadTimetable(id)
    }

    fun selectMosque(id: String) {
        if (_state.value.expandedId != id) toggleMosque(id)
    }

    fun loadTimetable(id: String) {
        val m = rawMosques.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(timetables = it.timetables + (id to TtState.Loading)) }
        viewModelScope.launch {
            val b = timetableRepo.load(m)
            _state.update { it.copy(timetables = it.timetables + (id to TtState.Loaded(b))) }
        }
    }

    fun setCalendarMethod(m: CalendarMethod) {
        prefs.calendarMethod = m
        _state.update { it.copy(calendarMethod = m) }
    }

    fun setDayAdjustment(n: Int) {
        val v = n.coerceIn(-1, 1)
        prefs.dayAdjustment = v
        _state.update { it.copy(dayAdjustment = v) }
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun resolvePlace() {
        val p = _state.value.point ?: return
        val last = geocodeOrigin
        if (last != null && Geo.distanceMeters(last.lat, last.lon, p.lat, p.lon) < 2000) return
        geocodeOrigin = p
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                try {
                    if (!Geocoder.isPresent()) null else {
                        val a = Geocoder(getApplication(), Locale.getDefault()).getFromLocation(p.lat, p.lon, 1)?.firstOrNull()
                        listOfNotNull(a?.locality ?: a?.subAdminArea, a?.countryName).joinToString(", ").ifBlank { null }
                    }
                } catch (e: Exception) {
                    null
                }
            }
            _state.update { it.copy(placeName = name) }
        }
    }
}
