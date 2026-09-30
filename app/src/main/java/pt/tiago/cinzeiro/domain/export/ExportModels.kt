package pt.tiago.cinzeiro.domain.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Registo tal como é exportado (id da BD + instante + zona). Sem Android: testável na JVM. */
data class ExportEvent(val id: Long, val instant: Instant, val zone: ZoneId) {
    /** `2026-09-29T12:32:05Z` — ISO-8601 UTC; fracção de segundo só aparece se não for zero (sem perdas). */
    val instantUtc: String get() = instant.toString()

    /** `2026-09-29T13:32:05` — hora local na zona do registo, ao segundo (informativo; a importação ignora-o). */
    val localDateTime: String
        get() = instant.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS).format(LOCAL_FORMAT)

    private companion object {
        val LOCAL_FORMAT: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
    }
}

/** Contrato do ficheiro JSON (ADR §Decisão 10). `version` sobe se o formato mudar. */
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

/** Nome sugerido: `smoketracker-AAAA-MM-DD.csv|json`. */
fun exportFileName(date: java.time.LocalDate, extension: String): String = "smoketracker-$date.$extension"
