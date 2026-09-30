package pt.tiago.cinzeiro.domain.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Entry as exported (DB id + instant + zone). No Android: testable on the JVM. */
data class ExportEvent(val id: Long, val instant: Instant, val zone: ZoneId) {
    /** `2026-09-29T12:32:05Z` — ISO-8601 UTC; fraction of a second only appears if non-zero (lossless). */
    val instantUtc: String get() = instant.toString()

    /** `2026-09-29T13:32:05` — local time in the entry's zone, to the second (informational; import ignores it). */
    val localDateTime: String
        get() = instant.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS).format(LOCAL_FORMAT)

    private companion object {
        val LOCAL_FORMAT: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
    }
}

/** JSON file contract (ADR decision 10). `version` is bumped if the format changes. */
@Serializable
data class JsonExport(
    val format: String = FORMAT,
    val version: Int = VERSION,
    @SerialName("exported_at") val exportedAt: String,
    @SerialName("app_version") val appVersion: String,
    val count: Int,
    val events: List<JsonExportEvent>,
) {
    companion object {
        const val FORMAT = "cinzeiro-export"
        const val VERSION = 1
    }
}

@Serializable
data class JsonExportEvent(
    val id: Long,
    @SerialName("instant_utc") val instantUtc: String,
    @SerialName("zone_id") val zoneId: String,
    @SerialName("local_datetime") val localDateTime: String,
)

/** Suggested name: `smoketracker-YYYY-MM-DD.csv|json`. */
fun exportFileName(date: java.time.LocalDate, extension: String): String = "smoketracker-$date.$extension"
