package pt.tiago.cinzeiro.settings

/**
 * Padrões **explícitos** (não `EFFECT_CLICK` predefinido, que pode ser imperceptível em alguns
 * motores). Amplitude sempre `VibrationEffect.DEFAULT_AMPLITUDE`.
 */
sealed interface VibrationSpec {
    /** Um impulso de [millis] ms. */
    data class OneShot(val millis: Long) : VibrationSpec

    /** `timings` à maneira de `createWaveform`: pausa, vibra, pausa, vibra… (ms). */
    data class Waveform(val timings: List<Long>) : VibrationSpec {
        val totalMillis: Long get() = timings.sum()
    }
}

object VibrationPatterns {
    const val SHORT_MS = 60L
    const val STRONG_MS = 180L
    val DOUBLE_TIMINGS = listOf(0L, 50L, 80L, 50L)

    fun spec(type: VibrationType): VibrationSpec = when (type) {
        VibrationType.SHORT -> VibrationSpec.OneShot(SHORT_MS)
        VibrationType.DOUBLE -> VibrationSpec.Waveform(DOUBLE_TIMINGS)
        VibrationType.STRONG -> VibrationSpec.OneShot(STRONG_MS)
    }

    /** Descrição curta para o log de feedback (R3.0). */
    fun describe(spec: VibrationSpec): String = when (spec) {
        is VibrationSpec.OneShot -> "oneshot(${spec.millis}ms)"
        is VibrationSpec.Waveform -> "waveform(${spec.timings.joinToString("/")}ms)"
    }
}
