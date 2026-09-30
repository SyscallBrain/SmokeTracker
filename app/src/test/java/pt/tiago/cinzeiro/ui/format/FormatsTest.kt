package pt.tiago.cinzeiro.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.tiago.cinzeiro.ui.components.deltaLabel
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class FormatsTest {
    @Test
    fun `day header in PT-PT without dots`() {
        assertEquals("ter, 29 set", Formats.dayHeader(LocalDate.of(2026, 9, 29)))
        assertEquals("qua, 30 set", Formats.dayHeader(LocalDate.of(2026, 9, 30)))
        assertEquals("sáb, 3 out", Formats.dayHeader(LocalDate.of(2026, 10, 3)))
    }

    @Test
    fun `HH mm time and decimal with comma`() {
        assertEquals("08:05", Formats.time(LocalTime.of(8, 5)))
        assertEquals("2,4", Formats.decimal1(2.44))
        assertEquals("11,0", Formats.decimal1(11.0))
    }

    @Test
    fun `short durations`() {
        assertEquals("0 min", Formats.duration(Duration.ofSeconds(30)))
        assertEquals("45 min", Formats.duration(Duration.ofMinutes(45)))
        assertEquals("1 h 12 min", Formats.duration(Duration.ofMinutes(72)))
        assertEquals("3 h", Formats.duration(Duration.ofHours(3)))
    }

    @Test
    fun `delta with up and down arrow`() {
        assertEquals("↓ 8 % vs anterior", deltaLabel(-8.2))
        assertEquals("↑ 15 % vs anterior", deltaLabel(14.6))
        assertEquals("= 0 % vs anterior", deltaLabel(0.2))
    }

    @Test
    fun `short weekday, day and month, heatmap initials`() {
        assertEquals("ter", Formats.weekdayShort(LocalDate.of(2026, 9, 29)))
        assertEquals("29 set", Formats.dayMonth(LocalDate.of(2026, 9, 29)))
        assertEquals(listOf("S", "T", "Q", "Q", "S", "S", "D"), Formats.weekdayInitials)
    }
}
