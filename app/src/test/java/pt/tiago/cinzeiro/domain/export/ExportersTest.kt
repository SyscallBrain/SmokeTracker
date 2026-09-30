package pt.tiago.cinzeiro.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ExportersTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")

    private fun csv(events: List<ExportEvent>) = StringWriter().also { CsvExporter.write(events, it) }.toString()

    @Test
    fun `CSV com cabecalho e linha exactamente como no ADR`() {
        val out = csv(listOf(ExportEvent(1, Instant.parse("2026-09-29T12:32:05Z"), lisbon)))
        assertEquals(
            "id,instant_utc,zone_id,local_datetime\n1,2026-09-29T12:32:05Z,Europe/Lisbon,2026-09-29T13:32:05\n",
            out,
        )
        assertTrue("sem BOM nem CR", !out.contains('\uFEFF') && !out.contains('\r'))
    }

    @Test
    fun `CSV sem eventos so tem o cabecalho`() {
        assertEquals("id,instant_utc,zone_id,local_datetime\n", csv(emptyList()))
    }

    @Test
    fun `mudanca de hora 25-10-2026 01h30 em Lisboa - dois instantes, mesma hora local`() {
        val local = LocalDateTime.of(2026, 10, 25, 1, 30)
        val first = ZonedDateTime.ofLocal(local, lisbon, null).withEarlierOffsetAtOverlap().toInstant()
        val second = ZonedDateTime.ofLocal(local, lisbon, null).withLaterOffsetAtOverlap().toInstant()
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), first)
        assertEquals(Instant.parse("2026-10-25T01:30:00Z"), second)

        val lines = csv(listOf(ExportEvent(7, first, lisbon), ExportEvent(8, second, lisbon))).lines()
        val (a, b) = lines[1].split(",") to lines[2].split(",")
        assertNotEquals(a[1], b[1])
        assertEquals("2026-10-25T01:30:00", a[3])
        assertEquals(a[3], b[3])
        assertEquals(listOf("7", "2026-10-25T00:30:00Z", "Europe/Lisbon", "2026-10-25T01:30:00"), a)
        assertEquals(listOf("8", "2026-10-25T01:30:00Z", "Europe/Lisbon", "2026-10-25T01:30:00"), b)
    }

    @Test
    fun `local_datetime usa a zona do proprio registo e trunca ao segundo sem perder o instante`() {
        val e = ExportEvent(3, Instant.parse("2026-09-29T12:32:05.123Z"), ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-09-29T21:32:05", e.localDateTime)
        assertEquals("2026-09-29T12:32:05.123Z", e.instantUtc)
    }

    @Test
    fun `JSON tem format, version 1, count e faz round-trip`() {
        val events = listOf(
            ExportEvent(1, Instant.parse("2026-09-29T12:32:05Z"), lisbon),
            ExportEvent(2, Instant.parse("2026-10-25T00:30:00Z"), lisbon),
            ExportEvent(5, Instant.parse("2026-10-25T01:30:00.250Z"), ZoneId.of("America/New_York")),
        )
        val exportedAt = Instant.parse("2026-09-29T13:40:00.999Z")
        val text = StringWriter().also { JsonExporter.write(events, exportedAt, "0.3.0", it) }.toString()

        val doc = JsonExporter.read(text)
        assertEquals("cinzeiro-export", doc.format)
        assertEquals(1, doc.version)
        assertEquals(3, doc.count)
        assertEquals("2026-09-29T13:40:00Z", doc.exportedAt)
        assertEquals("0.3.0", doc.appVersion)
        val back = doc.events.map { ExportEvent(it.id, Instant.parse(it.instantUtc), ZoneId.of(it.zoneId)) }
        assertEquals(events, back)
        assertEquals(JsonExporter.build(events, exportedAt, "0.3.0"), doc)
    }

    @Test
    fun `JSON usa as chaves do ADR`() {
        val text = StringWriter().also {
            JsonExporter.write(listOf(ExportEvent(1, Instant.parse("2026-09-29T12:32:05Z"), lisbon)), Instant.parse("2026-09-29T13:40:00Z"), "1.0.0", it)
        }.toString()
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        assertEquals(listOf("format", "version", "exported_at", "app_version", "count", "events"), obj.keys.toList())
        val ev = (obj["events"] as kotlinx.serialization.json.JsonArray)[0] as kotlinx.serialization.json.JsonObject
        assertEquals(listOf("id", "instant_utc", "zone_id", "local_datetime"), ev.keys.toList())
        assertEquals("2026-09-29T13:32:05", (ev["local_datetime"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `nome sugerido do ficheiro`() {
        assertEquals("smoketracker-2026-09-29.csv", exportFileName(java.time.LocalDate.of(2026, 9, 29), "csv"))
    }
}
