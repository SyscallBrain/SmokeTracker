package pt.tiago.cinzeiro.debug

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Dados sintéticos para desenvolver gráficos: [days] dias até agora (inclui hoje até à hora
 * actual), 8–18 cigarros por dia, com picos ao acordar, depois de almoço e ao fim do dia.
 * Semente fixa → os mesmos dados em cada geração (o ecrã é comparável entre builds).
 */
object SyntheticData {
    /** Peso relativo de cada hora 0..23 (madrugada ~0, picos 8 h, 13–14 h, 21–22 h). */
    val HOUR_WEIGHTS = intArrayOf(
        1, 0, 0, 0, 0, 0, 1, 4, 9, 6, 5, 5,
        6, 9, 8, 5, 5, 6, 7, 6, 7, 9, 8, 4,
    )

    fun generate(clock: Clock, days: Int, seed: Int = 42): List<Pair<Instant, ZoneId>> {
        val zone = clock.zone
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val rnd = Random(seed)
        val totalWeight = HOUR_WEIGHTS.sum()
        val out = ArrayList<Pair<Instant, ZoneId>>()
        for (d in days - 1 downTo 0) {
            val date = today.minusDays(d.toLong())
            // Fim-de-semana um pouco mais; tendência ligeira de descida ao longo do período.
            val weekend = date.dayOfWeek.value >= 6
            val base = 14 - (days - 1 - d) * 3 / days.coerceAtLeast(1) + if (weekend) 2 else 0
            val n = (base + rnd.nextInt(-4, 5)).coerceIn(8, 18)
            repeat(n) {
                var pick = rnd.nextInt(totalWeight)
                var hour = 0
                while (pick >= HOUR_WEIGHTS[hour]) { pick -= HOUR_WEIGHTS[hour]; hour++ }
                val t = date.atTime(hour, rnd.nextInt(60), rnd.nextInt(60)).atZone(zone).toInstant()
                if (!t.isAfter(now)) out += t to zone
            }
        }
        out.sortBy { it.first }
        return out
    }
}
