package pt.tiago.cinzeiro.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.tiago.cinzeiro.data.LoggedEvent
import pt.tiago.cinzeiro.ui.components.EmptyState
import pt.tiago.cinzeiro.ui.format.Formats
import pt.tiago.cinzeiro.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Mesma janela do snackbar do ecrã Hoje (contrato de design: ~5 s). */
private const val UNDO_WINDOW_MS = 5_000L

@Composable
fun HistoryRoute(viewModel: HistoryViewModel, snackbarHostState: SnackbarHostState) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                is HistoryEvent.Deleted -> scope.launch {
                    val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                        snackbarHostState.showSnackbar(
                            message = "Apagado o registo das ${Formats.time(event.event.localTime())}",
                            actionLabel = "Desfazer",
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.event)
                }
                is HistoryEvent.Added -> scope.launch {
                    snackbarHostState.showSnackbar("Registado: ${Formats.dayHeader(event.date)}, ${Formats.time(event.time)}")
                }
                is HistoryEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.text) }
            }
        }
    }
    val context = LocalContext.current
    // Um launcher por tipo MIME (o contrato CreateDocument fixa o MIME na criação).
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)) { uri ->
        if (uri != null) viewModel.export(ExportFormat.CSV) { context.contentResolver.openOutputStream(uri, "wt") }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)) { uri ->
        if (uri != null) viewModel.export(ExportFormat.JSON) { context.contentResolver.openOutputStream(uri, "wt") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.prepareImport { context.contentResolver.openInputStream(uri) }
    }
    HistoryScreen(
        state = state,
        onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
        onConfirmImport = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
        onExport = { format ->
            val name = viewModel.suggestedFileName(format)
            when (format) {
                ExportFormat.CSV -> csvLauncher.launch(name)
                ExportFormat.JSON -> jsonLauncher.launch(name)
            }
        },
        onDelete = viewModel::delete,
        onLoadMore = viewModel::loadMore,
        onAdd = viewModel::addPast,
        now = viewModel::now,
        zoneNow = viewModel::zone,
    )
}

private fun LoggedEvent.localTime(): LocalTime = instant.atZone(zone).toLocalTime()

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onDelete: (LoggedEvent) -> Unit,
    onLoadMore: () -> Unit,
    onAdd: (LocalDate, LocalTime) -> Unit,
    now: () -> Instant,
    zoneNow: () -> java.time.ZoneId,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Paginação: ao chegar a 5 itens do fim, pede mais 30 dias.
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(listState, state.canLoadMore) {
        snapshotFlow { nearEnd }.collect { if (it && state.canLoadMore) onLoadMore() }
    }

    Column(Modifier.fillMaxSize()) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Dimens.screenPadding, end = Dimens.xs, top = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Histórico", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { exporting = true }) {
            Icon(Icons.Rounded.FileDownload, contentDescription = "Exportar ou importar")
        }
    }
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Unit
            state.isEmpty -> EmptyState("Ainda sem registos. Usa «Adicionar» para um registo esquecido.", icon = Icons.Rounded.History)
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.s, bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.days.isEmpty()) {
                    item { EmptyState("Sem registos nos últimos ${state.loadedDays} dias.") }
                }
                state.days.forEach { section ->
                    item(key = "h-${section.date}") { DayHeaderRow(section, state.today) }
                    items(section.events, key = { it.id }) { ev ->
                        EventRow(ev, onDelete)
                    }
                }
                if (state.canLoadMore) {
                    item(key = "more") {
                        TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("Carregar mais 30 dias")
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Adicionar") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Dimens.l),
        )
    }
    }

    if (exporting) {
        ExportSheet(
            enabled = !state.isEmpty,
            onDismiss = { exporting = false },
            onPick = { format ->
                exporting = false
                onExport(format)
            },
            onImport = {
                exporting = false
                onImport()
            },
        )
    }

    state.pendingImport?.let { pending ->
        ImportConfirmDialog(pending, onConfirm = onConfirmImport, onDismiss = onCancelImport)
    }

    if (adding) {
        AddPastEventDialog(
            now = now(),
            zone = zoneNow(),
            onDismiss = { adding = false },
            onConfirm = { d, t ->
                adding = false
                onAdd(d, t)
            },
        )
    }
}

/** «Importar N registos (M já existem e serão ignorados)?» — só aparece com N ≥ 1. */
@Composable
private fun ImportConfirmDialog(pending: PendingImport, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val n = pending.new
    val main = if (n == 1) "Importar 1 registo" else "Importar $n registos"
    val skipped = when (pending.existing) {
        0 -> ""
        1 -> " (1 já existe e será ignorado)"
        else -> " (${pending.existing} já existem e serão ignorados)"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar JSON") },
        text = { Text("$main$skipped?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Importar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DayHeaderRow(section: DaySection, today: LocalDate?) {
    val label = if (section.date == today) "Hoje · ${Formats.dayHeader(section.date)}" else Formats.dayHeader(section.date)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Dimens.l, bottom = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            section.events.size.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Composable
private fun EventRow(ev: LoggedEvent, onDelete: (LoggedEvent) -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val zoneNote = ev.zone.takeIf { it != java.time.ZoneId.systemDefault() }?.id
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete(ev) },
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = Dimens.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text("apagar", color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.padding(Dimens.xs))
                Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Apagar") { onDelete(ev); true })
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .heightIn(min = 48.dp)
                .padding(vertical = Dimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Formats.time(ev.localTime()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (zoneNote != null) {
                Spacer(Modifier.padding(Dimens.xs))
                Text(zoneNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Registo retroactivo: DatePicker M3 (dias futuros não seleccionáveis) → TimePicker M3 24 h.
 * «Guardar» fica desactivado se a combinação cair no futuro; o repositório volta a recusar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPastEventDialog(
    now: Instant,
    zone: java.time.ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalTime) -> Unit,
) {
    val nowLocal = now.atZone(zone)
    val today = nowLocal.toLocalDate()
    var pickedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    if (pickedDate == null) {
        val todayUtcMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = todayUtcMs,
            yearRange = 2000..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMs
                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        pickedDate = dateState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                    },
                ) { Text("Seguinte") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        ) {
            DatePicker(state = dateState, title = { Text("Dia do registo", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    } else {
        val date = pickedDate!!
        val timeState = rememberTimePickerState(
            initialHour = nowLocal.hour,
            initialMinute = nowLocal.minute,
            is24Hour = true,
        )
        val time = LocalTime.of(timeState.hour, timeState.minute)
        val valid = pastInstantOrNull(date, time, zone, now) != null
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Hora · ${Formats.dayHeader(date)}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                    if (!valid) {
                        Text(
                            "Não é possível registar no futuro",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { onConfirm(date, time) }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { pickedDate = null }) { Text("Voltar") } },
        )
    }
}
