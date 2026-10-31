package com.miqat.app.timetable

import com.batoulapps.adhan.CalculationMethod

/** User choice in Settings. AUTOMATIC picks the usual method for wherever the phone is. */
enum class MethodPref(val label: String, val method: CalculationMethod?) {
    AUTOMATIC("Automatic (usual method for where you are)", null),
    MWL("Muslim World League", CalculationMethod.MUSLIM_WORLD_LEAGUE),
    MOONSIGHTING("Moonsighting Committee (UK / Europe style)", CalculationMethod.MOON_SIGHTING_COMMITTEE),
    NORTH_AMERICA("North America (ISNA)", CalculationMethod.NORTH_AMERICA),
    EGYPTIAN("Egyptian General Authority", CalculationMethod.EGYPTIAN),
    KARACHI("Univ. of Islamic Sciences, Karachi", CalculationMethod.KARACHI),
    UMM_AL_QURA("Umm al-Qura (Saudi Arabia)", CalculationMethod.UMM_AL_QURA),
    DUBAI("Dubai (UAE)", CalculationMethod.DUBAI),
    KUWAIT("Kuwait", CalculationMethod.KUWAIT),
    QATAR("Qatar", CalculationMethod.QATAR),
    SINGAPORE("Singapore / Malaysia / Indonesia", CalculationMethod.SINGAPORE)
}

data class MethodChoice(val method: CalculationMethod, val label: String)

object RegionMethods {
    private val byCountry: Map<String, CalculationMethod> = buildMap {
        put("SA", CalculationMethod.UMM_AL_QURA)
        put("AE", CalculationMethod.DUBAI)
        put("KW", CalculationMethod.KUWAIT)
        put("QA", CalculationMethod.QATAR)
        for (c in listOf("EG", "SD", "SY", "LB", "IQ", "LY")) put(c, CalculationMethod.EGYPTIAN)
        for (c in listOf("PK", "BD", "AF", "IN")) put(c, CalculationMethod.KARACHI)
        for (c in listOf("SG", "MY", "ID", "BN")) put(c, CalculationMethod.SINGAPORE)
        for (c in listOf("US", "CA")) put(c, CalculationMethod.NORTH_AMERICA)
        for (c in listOf("GB", "IE", "GG", "JE", "IM")) put(c, CalculationMethod.MOON_SIGHTING_COMMITTEE)
    }

    fun nameOf(m: CalculationMethod): String = when (m) {
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> "Muslim World League"
        CalculationMethod.EGYPTIAN -> "Egyptian General Authority"
        CalculationMethod.KARACHI -> "Univ. of Islamic Sciences, Karachi"
        CalculationMethod.UMM_AL_QURA -> "Umm al-Qura"
        CalculationMethod.DUBAI -> "Dubai"
        CalculationMethod.MOON_SIGHTING_COMMITTEE -> "Moonsighting Committee"
        CalculationMethod.NORTH_AMERICA -> "North America (ISNA)"
        CalculationMethod.KUWAIT -> "Kuwait"
        CalculationMethod.QATAR -> "Qatar"
        CalculationMethod.SINGAPORE -> "Singapore / Malaysia / Indonesia"
        else -> "Standard"
    }

    /** Rough regions, used only when the country cannot be looked up (for example offline). */
    private fun guessByCoords(lat: Double, lon: Double): CalculationMethod? = when {
        lat in 49.8..60.9 && lon in -10.7..2.0 -> CalculationMethod.MOON_SIGHTING_COMMITTEE
        lat in 24.0..49.5 && lon in -125.0..-66.0 -> CalculationMethod.NORTH_AMERICA
        lat in 6.0..37.0 && lon in 60.0..93.0 -> CalculationMethod.KARACHI
        lat in -11.0..7.0 && lon in 95.0..141.0 -> CalculationMethod.SINGAPORE
        lat in 16.0..32.0 && lon in 35.0..55.0 -> CalculationMethod.UMM_AL_QURA
        lat in 22.0..31.7 && lon in 25.0..35.0 -> CalculationMethod.EGYPTIAN
        else -> null
    }

    fun choose(lat: Double, lon: Double, countryCode: String?, pref: MethodPref): MethodChoice {
        pref.method?.let { return MethodChoice(it, "${pref.label} (your choice)") }
        val cc = countryCode?.uppercase()
        val known = cc?.let { byCountry[it] }
        return when {
            known != null -> MethodChoice(known, "${nameOf(known)} (usual for $cc)")
            cc != null -> MethodChoice(CalculationMethod.MUSLIM_WORLD_LEAGUE, "Muslim World League (general default for $cc)")
            else -> {
                val g = guessByCoords(lat, lon)
                if (g != null) MethodChoice(g, "${nameOf(g)} (guessed from your position)")
                else MethodChoice(CalculationMethod.MUSLIM_WORLD_LEAGUE, "Muslim World League (general default)")
            }
        }
    }
}
