package agency.dynamicdata.steps.core

import java.util.Locale

/**
 * Kilograms or pounds, by where the phone is set to.
 *
 * Health Connect stores mass in one unit and leaves display to the app. Pounds for
 * the countries that weigh people in them, kilograms everywhere else.
 */
enum class WeightUnit(val symbol: String, private val perKilogram: Double) {
    KILOGRAMS("kg", 1.0),
    POUNDS("lb", 2.2046226218),
    ;

    fun fromKilograms(kilograms: Double): Double = kilograms * perKilogram

    companion object {
        private val POUND_COUNTRIES = setOf("US", "LR", "MM")

        fun forLocale(locale: Locale): WeightUnit =
            if (locale.country.uppercase() in POUND_COUNTRIES) POUNDS else KILOGRAMS
    }
}
