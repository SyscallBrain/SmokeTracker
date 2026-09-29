package pt.tiago.cinzeiro.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Um cigarro: o instante (UTC) e a zona onde foi fumado. */
data class SmokeEvent(
    val instant: Instant,
    val zone: ZoneId,
)

/** Dia local na zona do próprio evento (fronteira = meia-noite local). */
fun SmokeEvent.localDate(): LocalDate = instant.atZone(zone).toLocalDate()

/** Hora local 0..23 na zona do próprio evento. */
fun SmokeEvent.localHour(): Int = instant.atZone(zone).hour
