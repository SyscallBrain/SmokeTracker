package pt.tiago.cinzeiro.settings

/** Tipo de vibração do +1 (app e widget). Padrões explícitos em [VibrationPatterns]. */
enum class VibrationType(val label: String) {
    SHORT("Curta"),
    DOUBLE("Dupla"),
    STRONG("Forte"),
    ;

    companion object {
        /** Valor gravado desconhecido (versão futura, ficheiro corrompido) → omissão. */
        fun fromStored(value: String?): VibrationType = entries.firstOrNull { it.name == value } ?: SHORT
    }
}

data class Settings(
    val vibrate: Boolean = true,
    val vibrationType: VibrationType = VibrationType.SHORT,
    /** Só o Toast do +1 do widget; o snackbar da app tem «Desfazer» e mostra-se sempre. */
    val showMessage: Boolean = true,
)
