package pt.tiago.cinzeiro.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MidnightTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")

    @Test
    fun `dia normal`() {
        // 13:32:05 em Lisboa (WEST) → 10 h 27 min 55 s até à meia-noite.
        assertEquals(
            Duration.ofHours(10).plusMinutes(27).plusSeconds(55),
            durationUntilNextLocalMidnight(Instant.parse("2026-09-29T12:32:05Z"), lisbon),
        )
    }

    @Test
    fun `dia de 25 h - atrasar a hora a 25-10-2026`() {
        // 00:30 WEST de 25 out → meia-noite de 26 out (WET) = 24 h 30 min reais.
        assertEquals(
            Duration.ofHours(24).plusMinutes(30),
            durationUntilNextLocalMidnight(Instant.parse("2026-10-24T23:30:00Z"), lisbon),
        )
    }

    @Test
    fun `dia de 23 h - adiantar a hora a 29-03-2026`() {
        // 00:30 WET de 29 mar → meia-noite de 30 mar (WEST, 23:00Z) = 22 h 30 min.
        assertEquals(
            Duration.ofHours(22).plusMinutes(30),
            durationUntilNextLocalMidnight(Instant.parse("2026-03-29T00:30:00Z"), lisbon),
        )
    }

    @Test
    fun `mesmo instante noutra zona da outro dia local`() {
        // 23:30 em Lisboa = 07:30 do dia seguinte em Tóquio → 16 h 30 min até à meia-noite de Tóquio.
        assertEquals(
            Duration.ofHours(16).plusMinutes(30),
            durationUntilNextLocalMidnight(Instant.parse("2026-09-29T22:30:00Z"), ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun `zona em que a meia-noite nao existe comeca o dia a 01h`() {
        val santiago = ZoneId.of("America/Santiago")
        val now = Instant.parse("2026-09-05T16:00:00Z") // 12:00 de sábado, -04
        val expectedStart = LocalDate.of(2026, 9, 6).atStartOfDay(santiago).toInstant()
        assertEquals(Duration.between(now, expectedStart), durationUntilNextLocalMidnight(now, santiago))
    }

    @Test
    fun `exactamente a meia-noite espera o dia inteiro`() {
        val now = LocalDate.of(2026, 9, 30).atStartOfDay(lisbon).toInstant()
        assertEquals(Duration.ofHours(24), durationUntilNextLocalMidnight(now, lisbon))
    }

    @Test
    fun `rotulo do ultimo registo em hora absoluta`() {
        val today = LocalDate.of(2026, 9, 29)
        assertEquals("último 13:32", lastLabel(Instant.parse("2026-09-29T12:32:05Z"), lisbon, today, lisbon))
        assertEquals("último 28 set", lastLabel(Instant.parse("2026-09-28T22:10:00Z"), lisbon, today, lisbon))
        assertEquals("sem registos", lastLabel(null, null, today, lisbon))
    }
}
