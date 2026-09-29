package pt.tiago.cinzeiro.ui.format

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formatos PT-PT do contrato de design (JVM puro, testável). */
object Formats {
    val PT: Locale = Locale.forLanguageTag("pt-PT")
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm", PT)
    // Abreviaturas fixas: o `EEE` de pt-PT dá «terça»/«ter.» consoante a versão do CLDR (JVM vs
    // Android), e o ecrã tem de ser igual à maqueta («qua, 29 set») em qualquer lado.
    private val weekdays = arrayOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
    private val months = arrayOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    fun time(t: LocalTime): String = hhmm.format(t)

    /** «qua, 29 set» (equivalente a `EEE, d MMM` em PT-PT). */
    fun dayHeader(d: LocalDate): String =
        "${weekdays[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${months[d.monthValue - 1]}"

    /** «qua» — abreviatura fixa do dia da semana. */
    fun weekdayShort(d: LocalDate): String = weekdays[d.dayOfWeek.value - 1]

    /** «29 set». */
    fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

    /** Inicial do dia da semana, segunda = 0: S T Q Q S S D (heatmap). */
    val weekdayInitials: List<String> = listOf("S", "T", "Q", "Q", "S", "S", "D")

    /** Uma casa decimal com vírgula: 2,4. */
    fun decimal1(x: Double): String = String.format(PT, "%.1f", x)

    /** Percentagem inteira sem sinal: «8 %». */
    fun percentAbs(x: Double): String = "${abs(x).roundToInt()} %"

    /** Duração curta: «45 min», «1 h 12 min», «3 h». */
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
