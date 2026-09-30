package pt.tiago.cinzeiro.settings

/** Vibration type for the +1 (app and widget). Explicit patterns in [VibrationPatterns]. */
enum class VibrationType(val label: String) {
    SHORT("Curta"),
    DOUBLE("Dupla"),
    STRONG("Forte"),
    ;

    companion object {
        /** Unknown stored value (future version, corrupted file) → default. */
        fun fromStored(value: String?): VibrationType = entries.firstOrNull { it.name == value } ?: SHORT
    }
}

data class Settings(
    val vibrate: Boolean = true,
    val vibrationType: VibrationType = VibrationType.SHORT,
    /** Only the widget's +1 Toast; the app's snackbar has "Desfazer" and is always shown. */
    val showMessage: Boolean = true,
)
