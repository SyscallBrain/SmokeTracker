package pt.tiago.cinzeiro.ui.format

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** pt-PT formats from the design contract (pure JVM, testable). */
object Formats {
    val PT: Locale = Locale.forLanguageTag("pt-PT")
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm", PT)
    // Fixed abbreviations: pt-PT `EEE` yields "terça"/"ter." depending on the CLDR version (JVM vs
    // Android), and the screen must match the mockup ("qua, 29 set") everywhere.
    private val weekdays = arrayOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
    private val months = arrayOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    fun time(t: LocalTime): String = hhmm.format(t)

    /** "qua, 29 set" (equivalent to `EEE, d MMM` in pt-PT). */
    fun dayHeader(d: LocalDate): String =
        "${weekdays[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${months[d.monthValue - 1]}"

    /** "qua" — fixed weekday abbreviation. */
    fun weekdayShort(d: LocalDate): String = weekdays[d.dayOfWeek.value - 1]

    /** "29 set". */
    fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

    /** Weekday initial, Monday = 0: S T Q Q S S D (heatmap). */
    val weekdayInitials: List<String> = listOf("S", "T", "Q", "Q", "S", "S", "D")

    /** One decimal place with a comma: 2,4. */
    fun decimal1(x: Double): String = String.format(PT, "%.1f", x)

    /** Unsigned integer percentage: "8 %". */
    fun percentAbs(x: Double): String = "${abs(x).roundToInt()} %"

    /** Short duration: "45 min", "1 h 12 min", "3 h". */
    fun duration(d: Duration): String {
        val totalMin = d.toMinutes().coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }
}
