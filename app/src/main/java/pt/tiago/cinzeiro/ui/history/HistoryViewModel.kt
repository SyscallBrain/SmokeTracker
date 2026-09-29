package pt.tiago.cinzeiro.ui.history

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
import pt.tiago.cinzeiro.domain.localDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Tamanho de cada página do histórico, em dias (não se carregam anos de uma vez). */
const val HISTORY_PAGE_DAYS = 30

data class DaySection(val date: LocalDate, val events: List<LoggedEvent>)

data class HistoryUiState(
    val loading: Boolean = true,
    val today: LocalDate? = null,
    /** Dias com registos, do mais recente para o mais antigo; eventos de cada dia do mais recente para o mais antigo. */
    val days: List<DaySection> = emptyList(),
    /** Há registos mais antigos do que a janela carregada. */
    val canLoadMore: Boolean = false,
    val loadedDays: Int = HISTORY_PAGE_DAYS,
    val isEmpty: Boolean = true,
)

sealed interface HistoryEvent {
    data class Deleted(val event: LoggedEvent) : HistoryEvent
    data class Added(val date: LocalDate, val time: LocalTime) : HistoryEvent
    data class Message(val text: String) : HistoryEvent
}

/** Agrupa por dia local (zona do próprio registo), dias e registos do mais recente para o mais antigo. */
fun groupByDay(events: List<LoggedEvent>): List<DaySection> =
    events.groupBy { it.event.localDate() }
        .toSortedMap(compareByDescending { it })
        .map { (date, list) -> DaySection(date, list.sortedWith(compareByDescending<LoggedEvent> { it.instant }.thenByDescending { it.id })) }

/**
 * Valida um registo retroactivo: devolve o instante ou `null` se ficar no futuro face a [now].
 * A UI usa isto para desactivar o botão; o repositório volta a verificar (defesa em profundidade).
 */
fun pastInstantOrNull(date: LocalDate, time: LocalTime, zone: ZoneId, now: Instant): Instant? {
    val instant = date.atTime(time).atZone(zone).toInstant()
    return if (instant.isAfter(now)) null else instant
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repo: SmokeRepository) : ViewModel() {

    private val loadedDays = MutableStateFlow(HISTORY_PAGE_DAYS)
    private val resumeSignal = MutableStateFlow(0)
    private val minuteTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }
    private val today: Flow<LocalDate> = combine(resumeSignal, minuteTicker) { _, _ ->
        repo.now().atZone(repo.zone()).toLocalDate()
    }.distinctUntilChanged()

    val state: StateFlow<HistoryUiState> = combine(today, loadedDays) { d, n -> d to n }
        .flatMapLatest { (d, n) ->
            val zone = repo.zone()
            val from = d.minusDays(n - 1L).atStartOfDay(zone).toInstant()
            // Até ao fim de amanhã: registos de outra zona (viagem) com dia local «à frente» não se perdem.
            val to = d.plusDays(2).atStartOfDay(zone).toInstant()
            combine(repo.observeBetween(from, to), repo.observeFirstEventInstant()) { events, first ->
                HistoryUiState(
                    loading = false,
                    today = d,
                    days = groupByDay(events),
                    canLoadMore = first != null && first.isBefore(from),
                    loadedDays = n,
                    isEmpty = first == null,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = _events.receiveAsFlow()

    fun now(): Instant = repo.now()
    fun zone(): ZoneId = repo.zone()

    fun onResume() {
        resumeSignal.value++
    }

    /** Mais 30 dias para trás (chamado ao chegar perto do fim da lista). */
    fun loadMore() {
        if (state.value.canLoadMore) loadedDays.value += HISTORY_PAGE_DAYS
    }

    fun delete(event: LoggedEvent) {
        viewModelScope.launch {
            if (repo.delete(event.id)) _events.send(HistoryEvent.Deleted(event))
        }
    }

    /** Repõe o registo apagado com o **mesmo** instante e a **mesma** zona (o id é novo). */
    fun undoDelete(event: LoggedEvent) {
        viewModelScope.launch { repo.logAt(event.instant, event.zone) }
    }

    fun addPast(date: LocalDate, time: LocalTime) {
        viewModelScope.launch {
            val zone = repo.zone()
            val instant = pastInstantOrNull(date, time, zone, repo.now())
            if (instant == null) {
                _events.send(HistoryEvent.Message("Não é possível registar no futuro"))
                return@launch
            }
            try {
                repo.logAt(instant, zone)
                _events.send(HistoryEvent.Added(date, time))
            } catch (e: IllegalArgumentException) {
                _events.send(HistoryEvent.Message("Não é possível registar no futuro"))
            }
        }
    }
}
