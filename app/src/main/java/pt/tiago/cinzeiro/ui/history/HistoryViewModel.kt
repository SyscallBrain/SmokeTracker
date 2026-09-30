package pt.tiago.cinzeiro.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
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
import pt.tiago.cinzeiro.domain.export.CsvExporter
import pt.tiago.cinzeiro.domain.export.ExportEvent
import pt.tiago.cinzeiro.domain.export.JsonExporter
import pt.tiago.cinzeiro.domain.export.JsonImporter
import pt.tiago.cinzeiro.domain.SmokeEvent
import java.io.InputStream
import pt.tiago.cinzeiro.domain.export.exportFileName
import pt.tiago.cinzeiro.domain.localDate
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class ExportFormat(val mime: String, val extension: String) {
    CSV("text/csv", "csv"),
    JSON("application/json", "json"),
}

/**
 * MIME types accepted by the "Import JSON" picker: some file managers do not tag `.json` as
 * `application/json` (plan R6).
 */
val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/** Read limit for the import file (≈ 80,000 entries); anything larger is not one of our exports. */
const val IMPORT_MAX_BYTES = 16L * 1024 * 1024

/** File read and validated, awaiting user confirmation. */
data class PendingImport(val events: List<SmokeEvent>, val new: Int, val existing: Int)

/** Size of each history page, in days (years are not loaded at once). */
const val HISTORY_PAGE_DAYS = 30

data class DaySection(val date: LocalDate, val events: List<LoggedEvent>)

data class HistoryUiState(
    val loading: Boolean = true,
    val today: LocalDate? = null,
    /** Days with entries, newest first; each day's events newest first. */
    val days: List<DaySection> = emptyList(),
    /** There are entries older than the loaded window. */
    val canLoadMore: Boolean = false,
    val loadedDays: Int = HISTORY_PAGE_DAYS,
    val isEmpty: Boolean = true,
    /** Import read and awaiting confirmation (dialog); `null` otherwise. */
    val pendingImport: PendingImport? = null,
)

sealed interface HistoryEvent {
    data class Deleted(val event: LoggedEvent) : HistoryEvent
    data class Added(val date: LocalDate, val time: LocalTime) : HistoryEvent
    data class Message(val text: String) : HistoryEvent
}

/** Groups by local day (in each entry's own zone); days and entries newest first. */
fun groupByDay(events: List<LoggedEvent>): List<DaySection> =
    events.groupBy { it.event.localDate() }
        .toSortedMap(compareByDescending { it })
        .map { (date, list) -> DaySection(date, list.sortedWith(compareByDescending<LoggedEvent> { it.instant }.thenByDescending { it.id })) }

/**
 * Validates a retroactive entry: returns the instant, or `null` if it is in the future relative to [now].
 * The UI uses this to disable the button; the repository checks again (defence in depth).
 */
fun pastInstantOrNull(date: LocalDate, time: LocalTime, zone: ZoneId, now: Instant): Instant? {
    val instant = date.atTime(time).atZone(zone).toInstant()
    return if (instant.isAfter(now)) null else instant
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repo: SmokeRepository,
    private val appVersion: String = "",
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

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

    private val pendingImport = MutableStateFlow<PendingImport?>(null)

    val state: StateFlow<HistoryUiState> = combine(today, loadedDays) { d, n -> d to n }
        .flatMapLatest { (d, n) ->
            val zone = repo.zone()
            val from = d.minusDays(n - 1L).atStartOfDay(zone).toInstant()
            // Up to the end of tomorrow: entries from another zone (travel) whose local day is "ahead" are not lost.
            val to = d.plusDays(2).atStartOfDay(zone).toInstant()
            combine(repo.observeBetween(from, to), repo.observeFirstEventInstant(), pendingImport) { events, first, pending ->
                HistoryUiState(
                    loading = false,
                    today = d,
                    days = groupByDay(events),
                    canLoadMore = first != null && first.isBefore(from),
                    loadedDays = n,
                    isEmpty = first == null,
                    pendingImport = pending,
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

    /** 30 more days back (called when nearing the end of the list). */
    fun loadMore() {
        if (state.value.canLoadMore) loadedDays.value += HISTORY_PAGE_DAYS
    }

    fun delete(event: LoggedEvent) {
        viewModelScope.launch {
            if (repo.delete(event.id)) _events.send(HistoryEvent.Deleted(event))
        }
    }

    /** Restores the deleted entry with the **same** instant and the **same** zone (the id is new). */
    fun undoDelete(event: LoggedEvent) {
        viewModelScope.launch { repo.logAt(event.instant, event.zone) }
    }

    /**
     * Exports **all** entries to the destination chosen in SAF. [open] opens the `OutputStream`
     * (the UI uses `contentResolver.openOutputStream(uri, "wt")`); formatting lives in `domain/export`.
     */
    fun export(format: ExportFormat, open: () -> OutputStream?) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(io) {
                    val events = repo.allEvents().map { ExportEvent(it.id, it.instant, it.zone) }
                    val stream = open() ?: error("destino indisponível")
                    stream.bufferedWriter(Charsets.UTF_8).use { w ->
                        when (format) {
                            ExportFormat.CSV -> CsvExporter.write(events, w)
                            ExportFormat.JSON -> JsonExporter.write(events, repo.now(), appVersion, w)
                        }
                    }
                }
            }
            val text = result.fold(
                onSuccess = { n -> "Exportados $n registos" },
                onFailure = { e -> "Falhou a exportação: ${e.message ?: e.javaClass.simpleName}" },
            )
            _events.send(HistoryEvent.Message(text))
        }
    }

    /**
     * Import step 1: reads and validates **the whole file** ([open] opens the SAF `InputStream`),
     * counts new/existing entries and puts it in [HistoryUiState.pendingImport] for the dialog. Nothing is written.
     * Invalid file → message with the first error (and the entry index).
     */
    fun prepareImport(open: () -> InputStream?) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(io) {
                    val stream = open() ?: error("ficheiro indisponível")
                    val bytes = stream.use { it.readNBytesCompat(IMPORT_MAX_BYTES + 1) }
                    if (bytes.size > IMPORT_MAX_BYTES) error("ficheiro demasiado grande")
                    val parsed = JsonImporter.read(bytes.toString(Charsets.UTF_8), repo.now())
                    if (!parsed.ok) {
                        val first = parsed.errors.first().describe()
                        val more = parsed.errors.size - 1
                        error(if (more > 0) "$first (e mais $more)" else first)
                    }
                    val preview = repo.previewImport(parsed.events)
                    PendingImport(parsed.events, preview.new, preview.existing)
                }
            }
            result.fold(
                onSuccess = { pending ->
                    if (pending.new == 0) {
                        _events.send(HistoryEvent.Message(nothingNewText(pending)))
                    } else {
                        pendingImport.value = pending
                    }
                },
                onFailure = { e -> _events.send(HistoryEvent.Message("Ficheiro não importado: ${e.message ?: e.javaClass.simpleName}")) },
            )
        }
    }

    /** Step 2: the user confirmed. Inserts the new entries in one transaction and reports the actual count. */
    fun confirmImport() {
        val pending = pendingImport.value ?: return
        pendingImport.value = null
        viewModelScope.launch {
            val result = runCatching { withContext(io) { repo.importEvents(pending.events) } }
            val text = result.fold(
                onSuccess = { n -> if (n == 1) "Importado 1 registo" else "Importados $n registos" },
                onFailure = { e -> "Falhou a importação (nada foi importado): ${e.message ?: e.javaClass.simpleName}" },
            )
            _events.send(HistoryEvent.Message(text))
        }
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    /** Suggested name in the SAF picker: `smoketracker-YYYY-MM-DD.<ext>`. */
    fun suggestedFileName(format: ExportFormat): String =
        exportFileName(repo.now().atZone(repo.zone()).toLocalDate(), format.extension)

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

private fun nothingNewText(p: PendingImport): String = when (p.existing) {
    0 -> "O ficheiro não tem registos"
    1 -> "Nada a importar: o registo já existe"
    else -> "Nada a importar: os ${p.existing} registos já existem"
}

/** `InputStream.readNBytes(Int)` only exists from API 33 on Android; minSdk = 26. */
private fun InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8 * 1024)
    var total = 0L
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
