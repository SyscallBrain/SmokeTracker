package pt.tiago.cinzeiro.debug

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Synthetic data for developing charts: [days] days up to now (includes today up to the current
 * hour), 8–18 cigarettes per day, with peaks on waking, after lunch and in the evening.
 * Fixed seed → the same data on every generation (the screen is comparable across builds).
 */
object SyntheticData {
    /** Relative weight of each hour 0..23 (small hours ~0, peaks at 8 h, 13–14 h, 21–22 h). */
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
            // Slightly more at weekends; slight downward trend over the period.
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
