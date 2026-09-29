package pt.tiago.cinzeiro.domain

import java.time.Duration
import java.time.LocalDate

/** Períodos das estatísticas: N dias a terminar em `endInclusive`. */
enum class Period(val days: Int) {
    D7(7),
    D30(30),
    D90(90),
    Y1(365),
}

/**
 * @property perDay total / dias efectivos (ver [summary]).
 * @property maxDay dia com mais cigarros no período (o mais antigo, em caso de empate); `null` sem eventos.
 * @property avgInterval média dos intervalos entre eventos consecutivos do mesmo dia local.
 * @property deltaPct variação de [perDay] face ao período anterior, em pontos percentuais (50.0 = +50 %).
 */
data class Summary(
    val total: Int,
    val perDay: Double,
    val maxDay: DayCount?,
    val avgInterval: Duration?,
    val deltaPct: Double?,
)

/**
 * Resumo do período de [period] dias que termina em [endInclusive].
 *
 * - `perDay = total / min(N, endInclusive - firstEventDate + 1)`: na primeira semana de uso a
 *   média não aparece artificialmente baixa. Sem [firstEventDate] (ou posterior a [endInclusive]) é 0.
 * - O dia [endInclusive] conta como dia inteiro mesmo que ainda esteja a decorrer; para ignorar
 *   o dia parcial, passar `endInclusive = ontem`.
 * - `deltaPct` é `null` se o período anterior (os N dias antes do início) não estiver completo
 *   (começa antes de [firstEventDate]) ou tiver média 0.
 */
fun summary(
    events: List<SmokeEvent>,
    period: Period,
    endInclusive: LocalDate,
    firstEventDate: LocalDate?,
): Summary {
    val n = period.days
    val start = endInclusive.minusDays(n - 1L)
    val prevStart = start.minusDays(n.toLong())

    // Uma só passagem: contagens por dia do período actual + anterior (2N dias).
    val counts = IntArray(2 * n)
    val baseDay = prevStart.toEpochDay()
    val inPeriod = ArrayList<SmokeEvent>()
    for (e in events) {
        val idx = e.localDate().toEpochDay() - baseDay
        if (idx !in 0 until 2L * n) continue
        counts[idx.toInt()]++
        if (idx >= n) inPeriod += e
    }

    var total = 0
    var maxIdx = -1
    for (i in n until 2 * n) {
        total += counts[i]
        if (counts[i] > 0 && (maxIdx < 0 || counts[i] > counts[maxIdx])) maxIdx = i
    }
    val prevTotal = (0 until n).sumOf { counts[it] }

    val perDay = total.toDouble() / effectiveDays(n, endInclusive, firstEventDate).coerceAtLeast(1)
    val maxDay = if (maxIdx < 0) null else DayCount(prevStart.plusDays(maxIdx.toLong()), counts[maxIdx])

    val previousComplete = firstEventDate != null && firstEventDate <= prevStart
    val prevPerDay = prevTotal.toDouble() / n
    val deltaPct = if (previousComplete && prevPerDay > 0.0) (perDay - prevPerDay) / prevPerDay * 100.0 else null

    return Summary(total, perDay, maxDay, averageSameDayInterval(inPeriod), deltaPct)
}

private fun effectiveDays(n: Int, endInclusive: LocalDate, firstEventDate: LocalDate?): Int {
    if (firstEventDate == null) return n
    val sinceFirst = endInclusive.toEpochDay() - firstEventDate.toEpochDay() + 1
    return minOf(n.toLong(), sinceFirst).toInt()
}

/** Média dos intervalos entre eventos consecutivos do mesmo dia local; a noite não conta. */
private fun averageSameDayInterval(events: List<SmokeEvent>): Duration? {
    if (events.size < 2) return null
    val sorted = events.sortedBy { it.instant }
    var sumMs = 0L
    var pairs = 0
    var prev = sorted[0]
    var prevDate = prev.localDate()
    for (i in 1 until sorted.size) {
        val cur = sorted[i]
        val curDate = cur.localDate()
        if (curDate == prevDate) {
            sumMs += Duration.between(prev.instant, cur.instant).toMillis()
            pairs++
        }
        prev = cur
        prevDate = curDate
    }
    return if (pairs == 0) null else Duration.ofMillis(sumMs / pairs)
}
