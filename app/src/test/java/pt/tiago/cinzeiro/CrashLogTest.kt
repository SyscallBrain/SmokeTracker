package pt.tiago.cinzeiro

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashLogTest {
    @Test fun appends_to_empty() = assertEquals("a\n", CrashLog.keepLast("", listOf("a"), 200))

    @Test fun appends_to_existing() = assertEquals("a\nb\nc\n", CrashLog.keepLast("a\nb\n", listOf("c"), 200))

    @Test fun keeps_only_the_last_lines() =
        assertEquals("c\nd\ne\n", CrashLog.keepLast("a\nb\nc\nd\n", listOf("e"), 3))
}
