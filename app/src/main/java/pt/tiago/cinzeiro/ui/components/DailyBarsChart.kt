package pt.tiago.cinzeiro.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import pt.tiago.cinzeiro.ui.format.Formats
import pt.tiago.cinzeiro.ui.stats.BarsData
import pt.tiago.cinzeiro.ui.stats.Granularity
import kotlin.math.ceil
import kotlin.math.max

/**
 * Barras por dia (7 d/30 d) ou por semana (90 d/1 a), com a última (hoje / semana actual) em
 * `primary` e as restantes em `secondary`; média móvel 7 d em `tertiary` sobreposta quando
 * [BarsData.movingAvg] não está vazia. Tocar/premir uma barra mostra «valor · data» (marcador Vico).
 * API confirmada nas fontes da Vico 3.3.1 (`columnModel`/`lineModel`, não os obsoletos `*Series`).
 */
@Composable
fun DailyBarsChart(data: BarsData, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        modelProducer.runTransaction {
            columnModel { series(data.values) }
            if (data.movingAvg.isNotEmpty()) {
                lineModel { series(x = data.movingAvg.map { it.first }, y = data.movingAvg.map { it.second }) }
            }
        }
    }

    val thickness = when {
        data.values.size <= 7 -> 20.dp
        data.values.size <= 14 -> 12.dp
        data.values.size <= 31 -> 6.dp
        else -> 4.dp
    }
    val primaryColumn = remember(cs.primary, thickness) { LineComponent(Fill(cs.primary), thickness = thickness) }
    val secondaryColumn = remember(cs.secondary, thickness) { LineComponent(Fill(cs.secondary), thickness = thickness) }
    val highlight = data.highlightIndex
    val columnProvider = remember(highlight, primaryColumn, secondaryColumn) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore) =
                if (entry.x.toInt() == highlight) primaryColumn else secondaryColumn

            override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore) =
                secondaryColumn
        }
    }

    // Eixo Y em inteiros, partilhado entre barras e linha: até 4 marcas.
    val maxValue = max(data.values.maxOrNull() ?: 0, data.movingAvg.maxOfOrNull { ceil(it.second).toInt() } ?: 0)
    val maxY = max(2, maxValue)
    val step = max(1.0, ceil(maxY / 4.0))
    val topY = ceil(maxY / step) * step
    val lastX = (data.values.size - 1).coerceAtLeast(0).toDouble()
    val range = remember(topY, lastX) {
        CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = lastX, minY = 0.0, maxY = topY)
    }

    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val label = rememberAxisLabelComponent(style = labelStyle)
    val grid = rememberAxisGuidelineComponent(fill = Fill(cs.outline.copy(alpha = 0.5f)))
    val integer = remember { CartesianValueFormatter.decimal(decimalCount = 0) }
    val dates = data.dates
    val granularity = data.granularity
    val xFormatter = remember(dates, granularity) {
        CartesianValueFormatter { _, value, _ ->
            val d = dates.getOrNull(value.toInt()) ?: return@CartesianValueFormatter " "
            if (granularity == Granularity.DAILY && dates.size <= 7) Formats.weekdayShort(d) else Formats.dayMonth(d)
        }
    }
    val labelSpacing = when {
        data.values.size <= 7 -> 1
        data.values.size <= 14 -> 3
        data.values.size <= 31 -> 7
        else -> 13
    }

    val markerLabel = rememberTextComponent(
        style = MaterialTheme.typography.labelMedium.copy(color = cs.onSurface),
        padding = Insets(horizontal = 8.dp, vertical = 4.dp),
        background = rememberShapeComponent(fill = Fill(cs.surfaceVariant), shape = RoundedCornerShape(8.dp)),
    )
    val markerFormatter = remember(dates, granularity) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull() ?: return@ValueFormatter ""
            val column = targets.filterIsInstance<ColumnCartesianLayerMarkerTarget>().firstOrNull()
            val count = column?.columns?.firstOrNull()?.entry?.y?.toInt() ?: 0
            val d = dates.getOrNull(target.x.toInt())
            val what = when {
                d == null -> ""
                granularity == Granularity.DAILY -> Formats.dayHeader(d)
                else -> "semana de ${Formats.dayMonth(d)}"
            }
            "$count · $what"
        }
    }
    val marker = rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = markerFormatter,
        guideline = rememberAxisGuidelineComponent(fill = Fill(cs.outline)),
    )

    val maLine = LineCartesianLayer.rememberLine(
        fill = remember(cs.tertiary) { LineCartesianLayer.LineFill.single(Fill(cs.tertiary)) },
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
    )
    val layers = buildList {
        add(
            rememberColumnCartesianLayer(
                columnProvider = columnProvider,
                columnCollectionSpacing = if (data.values.size <= 7) 16.dp else 3.dp,
                rangeProvider = range,
            ),
        )
        if (data.movingAvg.isNotEmpty()) {
            add(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(maLine),
                    rangeProvider = range,
                ),
            )
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            *layers.toTypedArray(),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                label = label,
                tick = null,
                guideline = grid,
                valueFormatter = integer,
                itemPlacer = remember(step) { VerticalAxis.ItemPlacer.step({ step }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = rememberAxisLineComponent(fill = Fill(cs.outline)),
                label = label,
                tick = null,
                guideline = null,
                valueFormatter = xFormatter,
                itemPlacer = remember(labelSpacing) {
                    HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }, offset = { 0 })
                },
            ),
            marker = marker,
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth(),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        chartAreaHeight = 160.dp,
    )
}
