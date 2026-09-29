package pt.tiago.cinzeiro.widget

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Tempo real até ao início do dia local seguinte em [zone]. Usa `atStartOfDay(zone)`, que respeita
 * dias de 23/25 h e zonas onde a meia-noite não existe (salto às 00:00 → começa às 01:00).
 * JVM puro (testado sem Android).
 */
fun durationUntilNextLocalMidnight(now: Instant, zone: ZoneId): Duration {
    val next = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    return Duration.between(now, next)
}
