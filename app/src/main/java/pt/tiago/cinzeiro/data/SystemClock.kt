package pt.tiago.cinzeiro.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Relógio do sistema cuja zona é lida **em cada chamada**: se o Tiago viajar e o telemóvel
 * mudar de fuso, o registo seguinte fica com a zona nova (o `Clock.systemDefaultZone()`
 * congelava a zona do arranque da app).
 */
class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    override fun instant(): Instant = Instant.now()
}
