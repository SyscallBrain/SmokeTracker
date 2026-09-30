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
 * Um problema no ficheiro. [index] é a posição (base 0) em `events`, ou `null` se o erro for do
 * documento (JSON inválido, `format`, `version`, `count`).
 */
data class ImportError(val index: Int?, val message: String) {
    /** Texto para o utilizador; o registo é numerado a partir de 1 («registo n.º 3»). */
    fun describe(): String = if (index == null) message else "registo n.º ${index + 1}: $message"
}

/**
 * Resultado da leitura. **Tudo ou nada:** se [errors] não estiver vazio, [events] vem vazio.
 * [events] vêm pela ordem do ficheiro, instantes truncados ao milissegundo (a precisão da BD),
 * sem os `id` do ficheiro (a BD atribui novos).
 */
data class ImportResult(val events: List<SmokeEvent>, val errors: List<ImportError>) {
    val ok: Boolean get() = errors.isEmpty()
}

/**
 * Inverso do [JsonExporter] (ADR §Decisão 10, plano R6). Kotlin puro: testável na JVM.
 *
 * Valida o ficheiro inteiro antes de devolver qualquer evento: `format == cinzeiro-export`,
 * `version == 1`, `count == events.size`, e cada evento com `instant_utc` ISO-8601 válido, não
 * futuro face a [now], e `zone_id` IANA conhecido. `local_datetime` é informativo e ignorado.
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
