package pt.tiago.cinzeiro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.tiago.cinzeiro.ui.format.Formats
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme
import kotlin.math.sqrt

/** Intensidade 0..1 de uma célula; 0 fica na cor de fundo. Raiz quadrada: realça as células fracas. */
fun heatLevel(count: Int, max: Int): Float =
    if (max <= 0 || count <= 0) 0f else sqrt(count.toFloat() / max).coerceIn(0f, 1f)

/**
 * Heatmap 7×24 em Canvas: linha 0 = segunda (rótulos S T Q Q S S D), colunas = horas 0..23 com
 * rótulos em 0/6/12/18. Cor da célula = interpolação `surfaceVariant` → `primary`.
 */
@Composable
fun WeekdayHourHeatmap(grid: List<List<Int>>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val max = grid.maxOf { row -> row.maxOrNull() ?: 0 }
    val labelStyle = MaterialTheme.typography.labelSmall
    val rowHeight = 18.dp
    val gap = 2.dp
    val labelWidth = 16.dp
    val peak = grid.withIndex().flatMap { (d, row) -> row.withIndex().map { (h, c) -> Triple(d, h, c) } }
        .maxByOrNull { it.third }
    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = peak?.takeIf { it.third > 0 }
                    ?.let { "Mapa hora por dia da semana; pico ${Formats.weekdayInitials[it.first]} às ${it.second} h" }
                    ?: "Mapa hora por dia da semana vazio"
            },
    ) {
        Row {
            Column(Modifier.width(labelWidth)) {
                Formats.weekdayInitials.forEach { d ->
                    Box(Modifier.height(rowHeight + gap)) {
                        Text(d, style = labelStyle, color = cs.onSurfaceVariant)
                    }
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height((rowHeight + gap) * 7),
            ) {
                val gapPx = gap.toPx()
                val cellW = (size.width - gapPx * 23) / 24f
                val cellH = rowHeight.toPx()
                val radius = CornerRadius(2.dp.toPx())
                for (d in 0 until 7) {
                    for (h in 0 until 24) {
                        val level = heatLevel(grid[d][h], max)
                        drawRoundRect(
                            color = lerp(cs.surfaceVariant, cs.primary, level),
                            topLeft = Offset(h * (cellW + gapPx), d * (cellH + gapPx)),
                            size = Size(cellW, cellH),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }
        // Horas 0/6/12/18 alinhadas com o início da respectiva coluna.
        Row(Modifier.padding(start = labelWidth, top = 2.dp)) {
            listOf("0", "6", "12", "18").forEach { h ->
                Text(
                    h,
                    style = labelStyle,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(backgroundColor = 0xFF24283B, showBackground = true, widthDp = 360)
@Composable
private fun WeekdayHourHeatmapPreview() = CinzeiroTheme {
    val grid = List(7) { d -> List(24) { h -> if (h in 7..23) (h % 7 + d) % 9 else 0 } }
    WeekdayHourHeatmap(grid)
}
