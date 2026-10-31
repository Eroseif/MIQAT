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
import com.miqat.app.events.EventsDb
import com.miqat.app.events.UserEvent
import com.miqat.app.hijri.CalendarMethod
import com.miqat.app.timetable.AreaRepository
import com.miqat.app.timetable.CalculatedTimes
import com.miqat.app.timetable.MawaqitSource
import com.miqat.app.timetable.MethodPref
import com.miqat.app.timetable.RegionMethods
import com.miqat.app.timetable.Timetable
import com.miqat.app.timetable.TimetableBundle
import com.miqat.app.timetable.TimetableRepository
import com.miqat.app.zakat.MarketPrices
import com.miqat.app.zakat.NisabBasis
import com.miqat.app.zakat.PriceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

data class NearbyOfficialUi(val name: String, val distanceM: Double, val timetable: Timetable)

/** Prayer times for the area around the phone: official mosque timetables nearby + calculated times. */
data class AreaState(
    val calculated: Timetable? = null,
    val methodLabel: String = "",
    val official: List<NearbyOfficialUi> = emptyList(),
    val officialLoading: Boolean = false,
    val officialStale: Boolean = false,
    val officialNote: String? = null
)

data class PricesState(
    val prices: MarketPrices? = null,
    val loading: Boolean = false,
    val stale: Boolean = false
)

data class UiState(
    val point: LatLon? = null,
    val manual: Boolean = false,
    val placeName: String? = null,
    val countryCode: String? = null,
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
    val area: AreaState = AreaState(),
    val methodPref: MethodPref = MethodPref.AUTOMATIC,
    val prices: PricesState = PricesState(),
    val nisabBasis: NisabBasis = NisabBasis.SILVER,
    val zakatCurrency: String? = null,
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
    private val mawaqit = MawaqitSource(http)
    private val mosqueRepo = MosqueRepository(db.cache(), http)
    private val timetableRepo = TimetableRepository(db.cache(), listOf(mawaqit))
    private val areaRepo = AreaRepository(db.cache(), mawaqit)
    private val priceRepo = PriceRepository(db.cache(), http)
    private var pricesJob: Job? = null

    private val eventsDb = EventsDb.build(app)

    /** Birthdays and special days added by the user (stored on this phone only). */
    val events: StateFlow<List<UserEvent>> =
        eventsDb.events().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(
        UiState(
            calendarMethod = prefs.calendarMethod,
            dayAdjustment = prefs.dayAdjustment,
            methodPref = prefs.methodPref,
            nisabBasis = prefs.nisabBasis,
            zakatCurrency = prefs.zakatCurrency,
            countryCode = prefs.countryCode
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    var askedForPermission = false
    var permissionDenials = 0

    private var rawMosques: List<Mosque> = emptyList()
    private var searchOrigin: LatLon? = null
    private var areaOrigin: LatLon? = null
    private var geocodeOrigin: LatLon? = null
    private var countryPoint: LatLon? = null
    private var searchJob: Job? = null
    private var areaJob: Job? = null

    init {
        prefs.loadLocation()?.let { s ->
            val p = LatLon(s.lat, s.lon)
            countryPoint = p
            _state.update { it.copy(point = p, manual = s.manual) }
            startSearch()
            refreshArea(fetchOfficial = true)
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

    /** New day (or app reopened after midnight / in another time zone): refresh today's times. */
    fun refreshIfNewDay() {
        val calc = _state.value.area.calculated
        if (_state.value.point != null && (calc == null || calc.date != LocalDate.now().toString())) {
            refreshArea(fetchOfficial = true)
        }
    }

    private fun applyPoint(p: LatLon, manual: Boolean) {
        prefs.saveLocation(SavedLocation(p.lat, p.lon, manual))
        _state.update { it.copy(point = p, manual = manual) }
        recomputeDistances()
        val moved = searchOrigin?.let { Geo.distanceMeters(it.lat, it.lon, p.lat, p.lon) > 1000 } ?: true
        if (moved) startSearch()
        val areaMoved = areaOrigin?.let { Geo.distanceMeters(it.lat, it.lon, p.lat, p.lon) > 1000 } ?: true
        refreshArea(fetchOfficial = areaMoved)
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

    /** Recomputes the calculated times for the area; optionally re-scans for official mosque timetables. */
    private fun refreshArea(fetchOfficial: Boolean) {
        val s = _state.value
        val p = s.point ?: return
        val choice = RegionMethods.choose(p.lat, p.lon, s.countryCode, s.methodPref)
        val calc = CalculatedTimes.compute(p.lat, p.lon, LocalDate.now(), choice)
        _state.update { it.copy(area = it.area.copy(calculated = calc, methodLabel = choice.label)) }
        if (!fetchOfficial) return

        areaOrigin = p
        areaJob?.cancel()
        areaJob = viewModelScope.launch {
            _state.update { it.copy(area = it.area.copy(official = emptyList(), officialLoading = true, officialNote = null, officialStale = false)) }
            val r = areaRepo.officialNearby(p.lat, p.lon)
            val ui = r.mosques
                .map { NearbyOfficialUi(it.name, Geo.distanceMeters(p.lat, p.lon, it.lat, it.lon), it.timetable) }
                .filter { it.distanceM <= 15000 }
                .sortedBy { it.distanceM }
            _state.update {
                it.copy(area = it.area.copy(official = ui, officialLoading = false, officialStale = r.stale, officialNote = r.note))
            }
        }
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
        val s = _state.value
        val choice = RegionMethods.choose(m.lat, m.lon, s.countryCode, s.methodPref)
        _state.update { it.copy(timetables = it.timetables + (id to TtState.Loading)) }
        viewModelScope.launch {
            val b = timetableRepo.load(m, choice)
            _state.update { it.copy(timetables = it.timetables + (id to TtState.Loaded(b))) }
        }
    }

    fun addEvent(date: LocalDate, title: String, description: String, yearly: Boolean) {
        viewModelScope.launch {
            eventsDb.events().insert(
                UserEvent(dateEpochDay = date.toEpochDay(), title = title.trim(), description = description.trim(), yearly = yearly)
            )
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch { eventsDb.events().delete(id) }
    }

    /** Gold/silver prices and exchange rates for the Zakat tab (cached, so cheap to call often). */
    fun refreshPrices(force: Boolean = false) {
        if (pricesJob?.isActive == true) return
        pricesJob = viewModelScope.launch {
            _state.update { it.copy(prices = it.prices.copy(loading = true)) }
            val r = priceRepo.load(force)
            _state.update {
                val old = it.prices.prices
                it.copy(
                    prices = when {
                        r.prices != null -> PricesState(r.prices, loading = false, stale = r.stale)
                        old != null -> PricesState(old, loading = false, stale = true)
                        else -> PricesState(null, loading = false, stale = false)
                    }
                )
            }
        }
    }

    fun setNisabBasis(b: NisabBasis) {
        prefs.nisabBasis = b
        _state.update { it.copy(nisabBasis = b) }
    }

    /** null = automatic (currency of the country you are in). */
    fun setZakatCurrency(code: String?) {
        prefs.zakatCurrency = code
        _state.update { it.copy(zakatCurrency = code) }
    }

    fun setMethodPref(pref: MethodPref) {
        prefs.methodPref = pref
        _state.update { it.copy(methodPref = pref, timetables = emptyMap()) }
        refreshArea(fetchOfficial = false)
        _state.value.expandedId?.let { loadTimetable(it) }
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
            val result: Pair<String?, String?>? = withContext(Dispatchers.IO) {
                try {
                    if (!Geocoder.isPresent()) null else {
                        val a = Geocoder(getApplication(), Locale.getDefault()).getFromLocation(p.lat, p.lon, 1)?.firstOrNull()
                        val place = listOfNotNull(a?.locality ?: a?.subAdminArea, a?.countryName).joinToString(", ").ifBlank { null }
                        Pair(place, a?.countryCode)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            val newCountry = result?.second
            val before = _state.value.countryCode
            val keep = countryPoint?.let { Geo.distanceMeters(it.lat, it.lon, p.lat, p.lon) < 100_000 } ?: false
            val country = newCountry ?: if (keep) before else null
            if (newCountry != null) countryPoint = p
            prefs.countryCode = country
            _state.update { it.copy(placeName = result?.first ?: it.placeName, countryCode = country) }
            if (country != before) refreshArea(fetchOfficial = false)
        }
    }
}
