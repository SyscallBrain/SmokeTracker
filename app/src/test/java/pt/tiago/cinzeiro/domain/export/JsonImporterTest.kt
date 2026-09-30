package pt.tiago.cinzeiro.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.cinzeiro.domain.SmokeEvent
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class JsonImporterTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val now = Instant.parse("2026-09-30T12:00:00Z")

    private fun export(events: List<ExportEvent>): String =
        StringWriter().also { JsonExporter.write(events, now, "0.7.0", it) }.toString()

    private fun fixture(): String =
        requireNotNull(javaClass.classLoader!!.getResource("smoketracker-export-2026-09-30.json")) { "fixture em falta" }
            .readText(Charsets.UTF_8)

    /** Documento mínimo com os eventos dados em JSON cru (para injectar erros). */
    private fun doc(events: List<String>, format: String = "\"cinzeiro-export\"", version: String = "1", count: Int = events.size) =
        """{"format":$format,"version":$version,"exported_at":"2026-09-30T00:00:00Z","app_version":"0.6.0","count":$count,"events":[${events.joinToString(",")}]}"""

    private fun ev(id: Int, instant: String, zone: String = "Europe/Lisbon") =
        """{"id":$id,"instant_utc":"$instant","zone_id":"$zone","local_datetime":"x"}"""

    @Test
    fun `round-trip com o JsonExporter devolve os mesmos instantes e zonas`() {
        val events = listOf(
            ExportEvent(1, Instant.parse("2026-09-29T12:32:05Z"), lisbon),
            ExportEvent(9, Instant.parse("2026-09-29T14:08:19.121Z"), ZoneId.of("Asia/Tokyo")),
            ExportEvent(4, Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("America/New_York")),
        )
        val r = JsonImporter.read(export(events), now)
        assertTrue(r.errors.toString(), r.ok)
        assertEquals(events.map { SmokeEvent(it.instant, it.zone) }, r.events)
    }

    @Test
    fun `fixture real do Tiago - 13 eventos em Lisboa, milissegundos preservados`() {
        val r = JsonImporter.read(fixture(), now)
        assertTrue(r.errors.toString(), r.ok)
        assertEquals(13, r.events.size)
        assertTrue(r.events.all { it.zone == lisbon })
        assertEquals(Instant.parse("2026-09-29T14:08:19.121Z"), r.events.first().instant)
        // O ficheiro real tem instantes repetidos? Não deve: cada toque é um evento distinto.
        assertEquals(13, r.events.map { it.instant.toEpochMilli() to it.zone.id }.toSet().size)
    }

    @Test
    fun `mudanca de hora 25-10-2026 - dois 01h30 locais distintos preservados`() {
        val local = LocalDateTime.of(2026, 10, 25, 1, 30)
        val first = ZonedDateTime.ofLocal(local, lisbon, null).withEarlierOffsetAtOverlap().toInstant()
        val second = ZonedDateTime.ofLocal(local, lisbon, null).withLaterOffsetAtOverlap().toInstant()
        val later = Instant.parse("2026-11-01T00:00:00Z")
        val text = StringWriter().also {
            JsonExporter.write(listOf(ExportEvent(1, first, lisbon), ExportEvent(2, second, lisbon)), later, "0.7.0", it)
        }.toString()
        val r = JsonImporter.read(text, later)
        assertTrue(r.ok)
        assertEquals(listOf(Instant.parse("2026-10-25T00:30:00Z"), Instant.parse("2026-10-25T01:30:00Z")), r.events.map { it.instant })
        assertEquals(2, r.events.toSet().size)
    }

    @Test
    fun `ficheiro vazio de eventos e valido e devolve zero`() {
        val r = JsonImporter.read(doc(emptyList()), now)
        assertTrue(r.ok)
        assertTrue(r.events.isEmpty())
    }

    @Test
    fun `evento no futuro - erro com indice e nada importado`() {
        val r = JsonImporter.read(
            doc(listOf(ev(1, "2026-09-29T10:00:00Z"), ev(2, "2026-09-29T11:00:00Z"), ev(3, "2026-09-30T12:00:00.001Z"))),
            now,
        )
        assertFalse(r.ok)
        assertTrue(r.events.isEmpty())
        assertEquals(2, r.errors.single().index)
        assertTrue(r.errors.single().describe(), r.errors.single().describe().startsWith("registo n.º 3: instante no futuro"))
    }

    @Test
    fun `instante igual a agora e aceite`() {
        assertTrue(JsonImporter.read(doc(listOf(ev(1, "2026-09-30T12:00:00Z"))), now).ok)
    }

    @Test
    fun `zona desconhecida - erro com indice e nada importado`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z"), ev(2, "2026-09-29T11:00:00Z", "Mars/Olympus"))), now)
        assertTrue(r.events.isEmpty())
        assertEquals(ImportError(1, "zona desconhecida «Mars/Olympus»"), r.errors.single())
        assertEquals("registo n.º 2: zona desconhecida «Mars/Olympus»", r.errors.single().describe())
    }

    @Test
    fun `instante mal formado - erro com indice`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "29/09/2026 10:00"))), now)
        assertTrue(r.events.isEmpty())
        assertEquals(0, r.errors.single().index)
    }

    @Test
    fun `varios erros - todos listados, o primeiro e o de menor indice`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z", "X/Y"), ev(2, "2027-01-01T00:00:00Z"))), now)
        assertEquals(listOf(0, 1), r.errors.map { it.index })
        assertTrue(r.events.isEmpty())
    }

    @Test
    fun `format errado e rejeitado`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z")), format = "\"outra-app\""), now)
        assertTrue(r.events.isEmpty())
        assertNull(r.errors.single().index)
        assertTrue(r.errors.single().message.contains("outra-app"))
    }

    @Test
    fun `version 2 e rejeitada`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z")), version = "2"), now)
        assertTrue(r.events.isEmpty())
        assertNull(r.errors.single().index)
        assertTrue(r.errors.single().message, r.errors.single().message.startsWith("versão 2"))
    }

    @Test
    fun `count diferente do numero de eventos e rejeitado`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z")), count = 2), now)
        assertTrue(r.events.isEmpty())
        assertTrue(r.errors.single().message, r.errors.single().message.startsWith("count = 2 mas o ficheiro tem 1"))
    }

    @Test
    fun `nao JSON, JSON que nao e objecto e campos em falta sao rejeitados sem excepcao`() {
        for (text in listOf("", "isto não é json", "[1,2]", """{"format":"cinzeiro-export","version":1}""")) {
            val r = JsonImporter.read(text, now)
            assertFalse(text, r.ok)
            assertTrue(r.events.isEmpty())
            assertNull(r.errors.single().index)
        }
    }

    @Test
    fun `chaves desconhecidas sao ignoradas`() {
        val text = doc(listOf("""{"id":1,"instant_utc":"2026-09-29T10:00:00Z","zone_id":"UTC","local_datetime":"x","nota":"extra"}"""))
        assertTrue(JsonImporter.read(text, now).ok)
    }
}
