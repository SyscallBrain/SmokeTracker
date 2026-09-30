package pt.tiago.cinzeiro.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * System clock whose zone is read **on every call**: if Tiago travels and the phone changes
 * time zone, the next entry gets the new zone (`Clock.systemDefaultZone()` would freeze the
 * zone from app startup).
 */
class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    override fun instant(): Instant = Instant.now()
}
