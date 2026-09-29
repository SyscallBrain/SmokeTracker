package pt.tiago.cinzeiro.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
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
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.math.ceil
import kotlin.math.max

/**
 * 24 colunas (0–23 h), hora actual em `primary`, restantes em `secondary`; grelha horizontal em
 * `outline` a 50 %; eixos em `onSurfaceVariant` `labelSmall`. API confirmada nas fontes da Vico
 * 3.3.1 (`compose-android-3.3.1-sources.jar`) e no guia estável.
 */
@Composable
fun HourlyBars(hourly: List<Int>, currentHour: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(hourly) {
        modelProducer.runTransaction { columnModel { series(hourly) } }
    }

    val primaryColumn = remember(cs.primary) { LineComponent(Fill(cs.primary), thickness = 6.dp) }
    val secondaryColumn = remember(cs.secondary) { LineComponent(Fill(cs.secondary), thickness = 6.dp) }
    val columnProvider = remember(currentHour, primaryColumn, secondaryColumn) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore) =
                if (entry.x.toInt() == currentHour) primaryColumn else secondaryColumn

            override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore) =
                secondaryColumn
        }
    }

    // Eixo Y em inteiros: até 4 marcas.
    val maxY = max(2, hourly.maxOrNull() ?: 0)
    val step = max(1.0, ceil(maxY / 4.0))
    val topY = ceil(maxY / step) * step

    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val label = rememberAxisLabelComponent(style = labelStyle)
    val grid = rememberAxisGuidelineComponent(fill = Fill(cs.outline.copy(alpha = 0.5f)))
    val integer = remember { CartesianValueFormatter.decimal(decimalCount = 0) }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = columnProvider,
                columnCollectionSpacing = 4.dp,
                rangeProvider = remember(topY) {
                    CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = 23.0, minY = 0.0, maxY = topY)
                },
            ),
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
                valueFormatter = integer,
                itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 6 }) },
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth(),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        chartAreaHeight = 120.dp,
    )
}
