package pt.tiago.cinzeiro.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.tiago.cinzeiro.domain.DayCount
import pt.tiago.cinzeiro.domain.Period
import pt.tiago.cinzeiro.domain.Summary
import pt.tiago.cinzeiro.ui.components.DailyBarsChart
import pt.tiago.cinzeiro.ui.components.EmptyState
import pt.tiago.cinzeiro.ui.components.PeriodSelector
import pt.tiago.cinzeiro.ui.components.SectionHeader
import pt.tiago.cinzeiro.ui.components.StatCard
import pt.tiago.cinzeiro.ui.format.Formats
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme
import pt.tiago.cinzeiro.ui.theme.Dimens
import java.time.Duration
import java.time.LocalDate

@Composable
fun StatsRoute(viewModel: StatsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    StatsScreen(state = state, selected = selected, onSelect = viewModel::select)
}

/**
 * [selected] vem directamente do selector (responde logo ao toque); [state] mantém os números do
 * período anterior até os novos estarem calculados — não há estado de «a carregar» entre períodos,
 * logo o ecrã não pisca.
 */
@Composable
fun StatsScreen(state: StatsUiState, selected: Period, onSelect: (Period) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.s),
        verticalArrangement = Arrangement.spacedBy(Dimens.m),
    ) {
        PeriodSelector(selected, onSelect)

        val s = state.summary
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
            StatCard("total", s?.total?.toString() ?: "—", Modifier.weight(1f), caption = state.period.caption())
            StatCard(
                "média / dia",
                s?.takeIf { it.total > 0 }?.perDay?.let(Formats::decimal1) ?: "—",
                Modifier.weight(1f),
                deltaPct = s?.deltaPct,
                caption = "sem período anterior",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
            StatCard(
                "pior dia",
                s?.maxDay?.count?.toString() ?: "—",
                Modifier.weight(1f),
                caption = s?.maxDay?.date?.let(Formats::dayHeader) ?: "sem registos",
            )
            StatCard(
                "intervalo médio",
                s?.avgInterval?.let(Formats::duration) ?: "—",
                Modifier.weight(1f),
                caption = "no mesmo dia",
            )
        }

        Card {
            val bars = state.bars
            val weekly = bars?.granularity == Granularity.WEEKLY
            SectionHeader(
                when {
                    weekly -> "Por semana"
                    bars?.movingAvg?.isNotEmpty() == true -> "Por dia · — média 7 d"
                    else -> "Por dia"
                },
            )
            if (bars == null || s == null || s.total == 0) {
                EmptyState(if (state.loading) "" else "Sem registos neste período.")
            } else {
                DailyBarsChart(bars)
            }
        }
        PatternsSection(state)
    }
}

/** Secção «Por hora do dia» + heatmap (T6). */
@Composable
internal fun PatternsSection(state: StatsUiState) {
    // Preenchido no T6.
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, Dimens.cardShape)
            .padding(Dimens.m),
        content = content,
    )
}

private fun Period.caption(): String = when (this) {
    Period.D7 -> "últimos 7 dias"
    Period.D30 -> "últimos 30 dias"
    Period.D90 -> "últimos 90 dias"
    Period.Y1 -> "últimos 12 meses"
}

@Preview(heightDp = 900, backgroundColor = 0xFF1A1B26, showBackground = true)
@Composable
private fun StatsScreenPreview() = CinzeiroTheme {
    val today = LocalDate.of(2026, 9, 29)
    val values = listOf(12, 9, 14, 11, 19, 10, 9)
    StatsScreen(
        state = StatsUiState(
            loading = false,
            period = Period.D7,
            today = today,
            summary = Summary(84, 12.0, DayCount(today.minusDays(2), 19), Duration.ofMinutes(81), 3.0),
            bars = BarsData(Granularity.DAILY, (6 downTo 0).map { today.minusDays(it.toLong()) }, values, 6),
        ),
        selected = Period.D7,
        onSelect = {},
    )
}
