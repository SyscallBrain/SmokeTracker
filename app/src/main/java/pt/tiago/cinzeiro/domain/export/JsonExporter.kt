package pt.tiago.cinzeiro.domain.export

import kotlinx.serialization.json.Json
import java.io.Writer
import java.time.Instant
import java.time.temporal.ChronoUnit

/** JSON versionado (ADR §Decisão 10), com `kotlinx-serialization-json` — o mesmo código servirá a importação. */
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

    /** Escreve o documento; devolve o n.º de eventos. */
    fun write(events: List<ExportEvent>, exportedAt: Instant, appVersion: String, out: Writer): Int {
        out.write(json.encodeToString(JsonExport.serializer(), build(events, exportedAt, appVersion)))
        out.write("\n")
        out.flush()
        return events.size
    }

    /** Leitura (usada nos testes de round-trip; base da importação futura). */
    fun read(text: String): JsonExport = json.decodeFromString(JsonExport.serializer(), text)
}
