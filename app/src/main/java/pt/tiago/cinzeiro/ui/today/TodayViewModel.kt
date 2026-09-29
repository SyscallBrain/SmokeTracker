package pt.tiago.cinzeiro.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.tiago.cinzeiro.data.LoggedEvent
import pt.tiago.cinzeiro.data.SmokeRepository
import pt.tiago.cinzeiro.debug.DebugTools
import pt.tiago.cinzeiro.domain.Period
import pt.tiago.cinzeiro.domain.hourlyDistribution
import pt.tiago.cinzeiro.domain.localDate
import pt.tiago.cinzeiro.domain.summary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate? = null,
    val countToday: Int = 0,
    val lastInstant: Instant? = null,
    /** Média/dia dos 7 dias **até ontem** (o dia parcial de hoje não entra); null sem histórico. */
    val avg7PerDay: Double? = null,
    val avg7DeltaPct: Double? = null,
    val avgIntervalToday: Duration? = null,
    val hourly: List<Int> = List(24) { 0 },
    val currentHour: Int = 0,
)

sealed interface TodayEvent {
    data class Logged(val id: Long, val at: LocalTime) : TodayEvent
    data class Message(val text: String) : TodayEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repo: SmokeRepository,
    private val debugTools: DebugTools? = null,
) : ViewModel() {

    private data class Tick(val date: LocalDate, val hour: Int)

    private val resumeSignal = MutableStateFlow(0)
    private val minuteTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    /** Dia e hora actuais; muda à meia-noite e a cada hora (ou ao voltar à app). */
    private val tick: Flow<Tick> = combine(resumeSignal, minuteTicker) { _, _ ->
        val local = repo.now().atZone(repo.zone())
        Tick(local.toLocalDate(), local.hour)
    }.distinctUntilChanged()

    val state: StateFlow<TodayUiState> = tick.flatMapLatest { t ->
        val zone = repo.zone()
        // 14 dias para trás: 7 da média + 7 do período anterior (para a variação).
        val from = t.date.minusDays(14).atStartOfDay(zone).toInstant()
        val to = t.date.plusDays(1).atStartOfDay(zone).toInstant()
        combine(
            repo.observeBetween(from, to),
            repo.observeLast(),
            repo.observeFirstEventInstant(),
        ) { events, last, first -> build(t, events, last, first) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private val _events = Channel<TodayEvent>(Channel.BUFFERED)
    val events: Flow<TodayEvent> = _events.receiveAsFlow()

    val hasDebugTools: Boolean get() = debugTools != null

    fun now(): Instant = repo.now()

    /** Chamar em ON_RESUME: recalcula o dia se a app esteve parada durante a meia-noite. */
    fun onResume() {
        resumeSignal.value++
    }

    fun logNow() {
        viewModelScope.launch {
            val at = repo.now().atZone(repo.zone()).toLocalTime()
            val id = repo.logNow()
            _events.send(TodayEvent.Logged(id, at))
        }
    }

    /** Desfaz **este** registo (pelo id), nunca «o último» — o widget pode ter registado entretanto. */
    fun undo(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun seedDebugData() {
        val tools = debugTools ?: return
        viewModelScope.launch {
            val n = tools.seed(90)
            _events.send(TodayEvent.Message("Gerados $n registos de teste (90 dias)"))
        }
    }

    fun clearAllDebug() {
        val tools = debugTools ?: return
        viewModelScope.launch {
            tools.clearAll()
            _events.send(TodayEvent.Message("Todos os registos apagados"))
        }
    }

    private fun build(t: Tick, logged: List<LoggedEvent>, last: LoggedEvent?, first: Instant?): TodayUiState {
        val zone = repo.zone()
        val events = logged.map { it.event }
        val today = events.filter { it.localDate() == t.date }
        val firstDate = first?.atZone(zone)?.toLocalDate()
        val yesterday = t.date.minusDays(1)
        val avg7 = if (firstDate != null && firstDate <= yesterday) {
            summary(events, Period.D7, endInclusive = yesterday, firstEventDate = firstDate)
        } else {
            null
        }
        return TodayUiState(
            loading = false,
            date = t.date,
            countToday = today.size,
            lastInstant = last?.instant,
            avg7PerDay = avg7?.perDay,
            avg7DeltaPct = avg7?.deltaPct,
            avgIntervalToday = summary(today, Period.D7, t.date, firstDate).avgInterval,
            hourly = hourlyDistribution(today).toList(),
            currentHour = t.hour,
        )
    }
}
