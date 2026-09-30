package pt.tiago.cinzeiro.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class VibrationPatternsTest {
    @Test fun short_is_one_shot_60ms() =
        assertEquals(VibrationSpec.OneShot(60), VibrationPatterns.spec(VibrationType.SHORT))

    @Test fun double_is_waveform_0_50_80_50() {
        val spec = VibrationPatterns.spec(VibrationType.DOUBLE) as VibrationSpec.Waveform
        assertEquals(listOf(0L, 50L, 80L, 50L), spec.timings)
        assertEquals(180L, spec.totalMillis)
    }

    @Test fun strong_is_one_shot_180ms() =
        assertEquals(VibrationSpec.OneShot(180), VibrationPatterns.spec(VibrationType.STRONG))

    @Test fun every_type_has_a_perceptible_pattern() {
        VibrationType.entries.forEach { type ->
            val total = when (val s = VibrationPatterns.spec(type)) {
                is VibrationSpec.OneShot -> s.millis
                is VibrationSpec.Waveform -> s.totalMillis
            }
            assert(total >= 50) { "$type: $total ms" }
        }
    }

    @Test fun describe_for_log() {
        assertEquals("oneshot(60ms)", VibrationPatterns.describe(VibrationPatterns.spec(VibrationType.SHORT)))
        assertEquals("waveform(0/50/80/50ms)", VibrationPatterns.describe(VibrationPatterns.spec(VibrationType.DOUBLE)))
    }

    @Test fun unknown_stored_type_falls_back_to_short() {
        assertEquals(VibrationType.SHORT, VibrationType.fromStored(null))
        assertEquals(VibrationType.SHORT, VibrationType.fromStored("TRIPLE"))
        assertEquals(VibrationType.STRONG, VibrationType.fromStored("STRONG"))
    }
}
