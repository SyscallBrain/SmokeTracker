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
    /** After the last event of the synthetic fixture (25-10-2026). */
    private val fixtureNow = Instant.parse("2026-10-26T12:00:00Z")

    private fun export(events: List<ExportEvent>): String =
        StringWriter().also { JsonExporter.write(events, now, "0.7.0", it) }.toString()

    private fun fixture(): String =
        requireNotNull(javaClass.classLoader!!.getResource("sample-export-v1.json")) { "fixture em falta" }
            .readText(Charsets.UTF_8)

    /** Minimal document with the given events as raw JSON (to inject errors). */
    private fun doc(events: List<String>, format: String = "\"cinzeiro-export\"", version: String = "1", count: Int = events.size) =
        """{"format":$format,"version":$version,"exported_at":"2026-09-30T00:00:00Z","app_version":"0.6.0","count":$count,"events":[${events.joinToString(",")}]}"""

    private fun ev(id: Int, instant: String, zone: String = "Europe/Lisbon") =
        """{"id":$id,"instant_utc":"$instant","zone_id":"$zone","local_datetime":"x"}"""

    @Test
    fun `round-trip with JsonExporter returns the same instants and zones`() {
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
    fun `synthetic fixture - 13 events in Lisbon, milliseconds, midnight and DST change`() {
        val r = JsonImporter.read(fixture(), fixtureNow)
        assertTrue(r.errors.toString(), r.ok)
        assertEquals(13, r.events.size)
        assertTrue(r.events.all { it.zone == lisbon })
        assertEquals(Instant.parse("2026-10-20T07:15:00.250Z"), r.events.first().instant)
        // Each tap is a distinct event: there are no repeated instants.
        assertEquals(13, r.events.map { it.instant.toEpochMilli() to it.zone.id }.toSet().size)
        // Local 23:59:41 and 00:03:05 fall on different local days.
        val days = r.events.map { it.instant.atZone(lisbon).toLocalDate() }
        assertEquals(3, days.count { it == java.time.LocalDate.of(2026, 10, 21) })
        assertEquals(2, days.count { it == java.time.LocalDate.of(2026, 10, 22) })
        // 25-10-2026: two local 01h30 (before and after the clocks go back) with distinct instants.
        val dst = r.events.filter { it.instant.atZone(lisbon).toLocalDateTime() == LocalDateTime.of(2026, 10, 25, 1, 30) }
        assertEquals(listOf(Instant.parse("2026-10-25T00:30:00Z"), Instant.parse("2026-10-25T01:30:00Z")), dst.map { it.instant })
    }

    @Test
    fun `DST change 25-10-2026 - two distinct local 01h30 preserved`() {
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
    fun `file with no events is valid and returns zero`() {
        val r = JsonImporter.read(doc(emptyList()), now)
        assertTrue(r.ok)
        assertTrue(r.events.isEmpty())
    }

    @Test
    fun `event in the future - error with index and nothing imported`() {
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
    fun `instant equal to now is accepted`() {
        assertTrue(JsonImporter.read(doc(listOf(ev(1, "2026-09-30T12:00:00Z"))), now).ok)
    }

    @Test
    fun `unknown zone - error with index and nothing imported`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z"), ev(2, "2026-09-29T11:00:00Z", "Mars/Olympus"))), now)
        assertTrue(r.events.isEmpty())
        assertEquals(ImportError(1, "zona desconhecida «Mars/Olympus»"), r.errors.single())
        assertEquals("registo n.º 2: zona desconhecida «Mars/Olympus»", r.errors.single().describe())
    }

    @Test
    fun `malformed instant - error with index`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "29/09/2026 10:00"))), now)
        assertTrue(r.events.isEmpty())
        assertEquals(0, r.errors.single().index)
    }

    @Test
    fun `several errors - all listed, the first is the lowest index`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z", "X/Y"), ev(2, "2027-01-01T00:00:00Z"))), now)
        assertEquals(listOf(0, 1), r.errors.map { it.index })
        assertTrue(r.events.isEmpty())
    }

    @Test
    fun `wrong format is rejected`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z")), format = "\"outra-app\""), now)
        assertTrue(r.events.isEmpty())
        assertNull(r.errors.single().index)
        assertTrue(r.errors.single().message.contains("outra-app"))
    }

    @Test
    fun `version 2 is rejected`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z")), version = "2"), now)
        assertTrue(r.events.isEmpty())
        assertNull(r.errors.single().index)
        assertTrue(r.errors.single().message, r.errors.single().message.startsWith("versão 2"))
    }

    @Test
    fun `count different from the number of events is rejected`() {
        val r = JsonImporter.read(doc(listOf(ev(1, "2026-09-29T10:00:00Z")), count = 2), now)
        assertTrue(r.events.isEmpty())
        assertTrue(r.errors.single().message, r.errors.single().message.startsWith("count = 2 mas o ficheiro tem 1"))
    }

    @Test
    fun `non-JSON, JSON that is not an object and missing fields are rejected without exception`() {
        for (text in listOf("", "isto não é json", "[1,2]", """{"format":"cinzeiro-export","version":1}""")) {
            val r = JsonImporter.read(text, now)
            assertFalse(text, r.ok)
            assertTrue(r.events.isEmpty())
            assertNull(r.errors.single().index)
        }
    }

    @Test
    fun `unknown keys are ignored`() {
        val text = doc(listOf("""{"id":1,"instant_utc":"2026-09-29T10:00:00Z","zone_id":"UTC","local_datetime":"x","nota":"extra"}"""))
        assertTrue(JsonImporter.read(text, now).ok)
    }
}
