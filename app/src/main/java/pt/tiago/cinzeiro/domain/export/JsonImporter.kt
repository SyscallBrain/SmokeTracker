package pt.tiago.cinzeiro.domain.export

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import pt.tiago.cinzeiro.domain.SmokeEvent
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * A problem in the file. [index] is the position (0-based) in `events`, or `null` if the error
 * concerns the document (invalid JSON, `format`, `version`, `count`).
 */
data class ImportError(val index: Int?, val message: String) {
    /** User-facing text; the entry is numbered from 1 ("registo n.º 3"). */
    fun describe(): String = if (index == null) message else "registo n.º ${index + 1}: $message"
}

/**
 * Result of the read. **All or nothing:** if [errors] is not empty, [events] is empty.
 * [events] come in file order, instants truncated to the millisecond (the DB precision),
 * without the file's `id`s (the DB assigns new ones).
 */
data class ImportResult(val events: List<SmokeEvent>, val errors: List<ImportError>) {
    val ok: Boolean get() = errors.isEmpty()
}

/**
 * Inverse of [JsonExporter] (ADR decision 10, plan R6). Pure Kotlin: testable on the JVM.
 *
 * Validates the whole file before returning any event: `format == cinzeiro-export`,
 * `version == 1`, `count == events.size`, and each event with a valid ISO-8601 `instant_utc`, not
 * in the future relative to [now], and a known IANA `zone_id`. `local_datetime` is informational and ignored.
 */
object JsonImporter {
    fun read(text: String, now: Instant): ImportResult {
        val root = try {
            JsonExporter.json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: return fail(null, "o ficheiro não é um JSON válido")

        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != JsonExport.FORMAT) {
            return fail(null, "formato «${format ?: "—"}» desconhecido (esperado «${JsonExport.FORMAT}»)")
        }
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
        if (version != JsonExport.VERSION) {
            return fail(null, "versão ${version ?: "—"} do ficheiro não suportada (esta app lê a versão ${JsonExport.VERSION})")
        }

        val doc = try {
            JsonExporter.json.decodeFromJsonElement(JsonExport.serializer(), root)
        } catch (e: SerializationException) {
            return fail(null, "estrutura inválida (${e.message?.lineSequence()?.firstOrNull() ?: "campos em falta"})")
        } catch (e: IllegalArgumentException) {
            return fail(null, "estrutura inválida (${e.message?.lineSequence()?.firstOrNull() ?: "campos em falta"})")
        }
        if (doc.count != doc.events.size) {
            return fail(null, "count = ${doc.count} mas o ficheiro tem ${doc.events.size} registos (ficheiro truncado?)")
        }

        val errors = mutableListOf<ImportError>()
        val events = ArrayList<SmokeEvent>(doc.events.size)
        doc.events.forEachIndexed { i, e ->
            val instant = try {
                Instant.parse(e.instantUtc).truncatedTo(ChronoUnit.MILLIS)
            } catch (ex: DateTimeParseException) {
                errors += ImportError(i, "instante inválido «${e.instantUtc}»")
                return@forEachIndexed
            }
            if (instant.isAfter(now)) {
                errors += ImportError(i, "instante no futuro (${e.instantUtc})")
                return@forEachIndexed
            }
            val zone = try {
                ZoneId.of(e.zoneId)
            } catch (ex: DateTimeException) {
                errors += ImportError(i, "zona desconhecida «${e.zoneId}»")
                return@forEachIndexed
            }
            events += SmokeEvent(instant, zone)
        }
        return if (errors.isEmpty()) ImportResult(events, emptyList()) else ImportResult(emptyList(), errors)
    }

    private fun fail(index: Int?, message: String) = ImportResult(emptyList(), listOf(ImportError(index, message)))
}
