package pt.tiago.cinzeiro.domain.export

import kotlinx.serialization.json.Json
import java.io.Writer
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Versioned JSON (ADR decision 10), with `kotlinx-serialization-json` — the same code will serve import. */
object JsonExporter {
    val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun build(events: List<ExportEvent>, exportedAt: Instant, appVersion: String): JsonExport =
        JsonExport(
            exportedAt = exportedAt.truncatedTo(ChronoUnit.SECONDS).toString(),
            appVersion = appVersion,
            count = events.size,
            events = events.map { JsonExportEvent(it.id, it.instantUtc, it.zone.id, it.localDateTime) },
        )

    /** Writes the document; returns the number of events. */
    fun write(events: List<ExportEvent>, exportedAt: Instant, appVersion: String, out: Writer): Int {
        out.write(json.encodeToString(JsonExport.serializer(), build(events, exportedAt, appVersion)))
        out.write("\n")
        out.flush()
        return events.size
    }

    /** Reading (used in round-trip tests; basis for the future import). */
    fun read(text: String): JsonExport = json.decodeFromString(JsonExport.serializer(), text)
}
