package pt.tiago.cinzeiro.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import pt.tiago.cinzeiro.data.SmokeRepository
import pt.tiago.cinzeiro.domain.Period
import pt.tiago.cinzeiro.domain.SmokeEvent
import pt.tiago.cinzeiro.domain.Summary
import pt.tiago.cinzeiro.domain.dailyCounts
import pt.tiago.cinzeiro.domain.hourlyDistribution
import pt.tiago.cinzeiro.domain.localDate
import pt.tiago.cinzeiro.domain.movingAverage
import pt.tiago.cinzeiro.domain.summary
import pt.tiago.cinzeiro.domain.weekdayHourHeatmap
import pt.tiago.cinzeiro.domain.weeklyCounts
import java.time.LocalDate
import java.time.ZoneId

/** Barras por dia em 7 d/30 d; por semana (a começar à segunda) em 90 d/1 a. */
enum class Granularity { DAILY, WEEKLY }

/**
 * Dados do gráfico de barras. [dates] = dia (DAILY) ou segunda-feira da semana (WEEKLY), com o
 * mesmo índice que [values]. [movingAvg] = pares (índice da barra, média móvel 7 d), só em 30 d.
 */
data class BarsData(
    val granularity: Granularity,
    val dates: List<LocalDate>,
    val values: List<Int>,
    val highlightIndex: Int,
    val movingAvg: List<Pair<Int, Double>> = emptyList(),
)

data class StatsUiState(
    val loading: Boolean = true,
    val period: Period = Period.D7,
    val today: LocalDate? = null,
    val summary: Summary? = null,
    val bars: BarsData? = null,
    /** Soma por hora local (0..23) dos eventos do período. */
    val hourly: List<Int> = List(24) { 0 },
    /** Dia da semana (0 = segunda) × hora, eventos do período. */
    val heatmap: List<List<Int>> = List(7) { List(24) { 0 } },
    val currentHour: Int = 0,
)

/**
 * Cálculo puro do ecrã (testável sem Android): [events] deve cobrir pelo menos o período anterior
 * (para o `deltaPct`) e os 6 dias antes do início (para a média móvel).
 */
fun buildStats(
    events: List<SmokeEvent>,
    firstEventDate: LocalDate?,
    period: Period,
    today: LocalDate,
    currentHour: Int = 0,
): StatsUiState {
    val start = today.minusDays(period.days - 1L)
    val inPeriod = events.filter { val d = it.localDate(); d >= start && d <= today }
    val s = summary(events, period, today, firstEventDate)
    val bars = when (period) {
        Period.D7, Period.D30 -> {
            val daily = dailyCounts(events, start, today)
            BarsData(
                granularity = Granularity.DAILY,
                dates = daily.map { it.date },
                values = daily.map { it.count },
                highlightIndex = daily.lastIndex,
                movingAvg = if (period == Period.D30) movingAverageBars(events, firstEventDate, start, today) else emptyList(),
            )
        }
        // Em 90 d/1 a as barras são semanas: uma média móvel de 7 d amostrada à semana coincide com
        // o topo das barras (÷7), por isso não se sobrepõe (desvio registado no relatório do T5).
        Period.D90, Period.Y1 -> {
            val weekly = weeklyCounts(events, start, today)
            BarsData(
                granularity = Granularity.WEEKLY,
                dates = weekly.map { it.weekStart },
                values = weekly.map { it.count },
                highlightIndex = weekly.lastIndex,
            )
        }
    }
    return StatsUiState(
        loading = false,
        period = period,
        today = today,
        summary = s,
        bars = bars,
        hourly = hourlyDistribution(inPeriod).toList(),
        heatmap = weekdayHourHeatmap(inPeriod).map { it.toList() },
        currentHour = currentHour,
    )
}

/**
 * Média móvel 7 d por dia de [start]..[today], alinhada com o índice das barras. Só aparece a partir
 * do 7.º dia desde o primeiro registo (antes disso a média misturaria dias sem app instalada).
 */
private fun movingAverageBars(
    events: List<SmokeEvent>,
    firstEventDate: LocalDate?,
    start: LocalDate,
    today: LocalDate,
): List<Pair<Int, Double>> {
    if (firstEventDate == null) return emptyList()
    val from = maxOf(start.minusDays(6), firstEventDate)
    if (from > today) return emptyList()
    val daily = dailyCounts(events, from, today)
    val ma = movingAverage(daily, 7)
    return daily.indices.mapNotNull { i ->
        val value = ma[i] ?: return@mapNotNull null
        val barIndex = (daily[i].date.toEpochDay() - start.toEpochDay()).toInt()
        if (barIndex >= 0) barIndex to value else null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val repo: SmokeRepository,
    /** Onde se calcula (1 a = ~2 anos de eventos); nos testes, o dispatcher de teste. */
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val period = MutableStateFlow(Period.D7)
    private val resumeSignal = MutableStateFlow(0)
    private val minuteTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    private data class Tick(val date: LocalDate, val hour: Int)

    /** Dia e hora actuais; mudam à meia-noite / a cada hora (ou ao voltar à app). */
    private val tick: Flow<Tick> = combine(resumeSignal, minuteTicker) { _, _ ->
        val local = repo.now().atZone(repo.zone())
        Tick(local.toLocalDate(), local.hour)
    }.distinctUntilChanged()

    val state: StateFlow<StatsUiState> = combine(tick, period) { t, p -> t to p }
        .flatMapLatest { (t, p) ->
            val d = t.date
            val zone: ZoneId = repo.zone()
            // Período anterior (delta) + 6 dias antes (média móvel) + período actual.
            val from = d.minusDays(2L * p.days + 6).atStartOfDay(zone).toInstant()
            val to = d.plusDays(1).atStartOfDay(zone).toInstant()
            combine(repo.observeBetween(from, to), repo.observeFirstEventInstant()) { logged, first ->
                buildStats(logged.map { it.event }, first?.atZone(zone)?.toLocalDate(), p, d, t.hour)
            }.flowOn(compute)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    val selectedPeriod: StateFlow<Period> = period

    fun select(p: Period) {
        period.value = p
    }

    fun onResume() {
        resumeSignal.value++
    }
}
