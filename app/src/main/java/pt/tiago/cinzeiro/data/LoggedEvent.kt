package pt.tiago.cinzeiro.data

import pt.tiago.cinzeiro.domain.SmokeEvent
import java.time.Instant
import java.time.ZoneId

/** Evento guardado: o [SmokeEvent] do domínio mais o id da BD (para apagar/desfazer). */
data class LoggedEvent(val id: Long, val event: SmokeEvent) {
    val instant: Instant get() = event.instant
    val zone: ZoneId get() = event.zone
}

internal fun SmokeEventEntity.toLogged(): LoggedEvent =
    LoggedEvent(id, SmokeEvent(Instant.ofEpochMilli(atEpochMs), ZoneId.of(zoneId)))
