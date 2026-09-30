package pt.tiago.cinzeiro.widget

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Real time until the start of the next local day in [zone]. Uses `atStartOfDay(zone)`, which handles
 * 23/25 h days and zones where midnight does not exist (jump at 00:00 → day starts at 01:00).
 * Pure JVM (tested without Android).
 */
fun durationUntilNextLocalMidnight(now: Instant, zone: ZoneId): Duration {
    val next = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    return Duration.between(now, next)
}
