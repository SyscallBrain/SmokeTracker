package pt.tiago.cinzeiro.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One cigarette: the instant (UTC) and the zone where it was smoked. */
data class SmokeEvent(
    val instant: Instant,
    val zone: ZoneId,
)

/** Local day in the event's own zone (boundary = local midnight). */
fun SmokeEvent.localDate(): LocalDate = instant.atZone(zone).toLocalDate()

/** Local hour 0..23 in the event's own zone. */
fun SmokeEvent.localHour(): Int = instant.atZone(zone).hour
