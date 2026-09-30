package pt.tiago.cinzeiro.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.tiago.cinzeiro.ui.components.EmptyState
import pt.tiago.cinzeiro.ui.components.HourlyBars
import pt.tiago.cinzeiro.ui.components.SectionHeader
import pt.tiago.cinzeiro.ui.components.StatCard
import pt.tiago.cinzeiro.ui.format.Formats
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme
import pt.tiago.cinzeiro.ui.theme.Dimens
import pt.tiago.cinzeiro.ui.theme.tabular
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Duration of the "Registado às HH:MM · Desfazer" snackbar (design contract: ~5 s). */
private const val UNDO_WINDOW_MS = 5_000L

@Composable
fun TodayRoute(
    viewModel: TodayViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenDesignShowcase: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlusOneFeedback: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                is TodayEvent.Logged -> scope.launch {
                    val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                        snackbarHostState.showSnackbar(
                            message = "Registado às ${Formats.time(event.at)}",
                            actionLabel = "Desfazer",
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.id)
                }
                is TodayEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.text) }
            }
        }
    }

    TodayScreen(
        state = state,
        now = viewModel::now,
        onPlusOne = {
            onPlusOneFeedback()
            viewModel.logNow()
        },
        onOpenSettings = onOpenSettings,
        debugActions = if (viewModel.hasDebugTools) {
            DebugActions(viewModel::seedDebugData, viewModel::clearAllDebug, onOpenDesignShowcase)
        } else {
            null
        },
    )
}

/** Debug-APK-only actions (long press on the date). */
class DebugActions(val seed: () -> Unit, val clearAll: () -> Unit, val openDesign: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    now: () -> Instant,
    onPlusOne: () -> Unit,
    onOpenSettings: () -> Unit = {},
    debugActions: DebugActions? = null,
) {
    val cs = MaterialTheme.colorScheme
    var showDebug by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.s),
        verticalArrangement = Arrangement.spacedBy(Dimens.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.date?.let(Formats::dayHeader) ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        enabled = debugActions != null,
                        onClick = {},
                        onLongClick = { showDebug = true },
                    ),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Definições", tint = cs.onSurfaceVariant)
            }
        }

        AnimatedContent(
            targetState = state.countToday,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { h -> if (up) h else -h } + fadeIn()) togetherWith
                    (slideOutVertically { h -> if (up) -h else h } + fadeOut())
            },
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.m),
            label = "contagem",
        ) { count ->
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.displayLarge.tabular(),
                color = cs.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = lastLabel(state.lastInstant, now),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onPlusOne,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = Dimens.s)
                .size(Dimens.plusButton)
                .semantics { contentDescription = "Registar um cigarro" },
        ) {
            Text("+1", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
            StatCard(
                label = "média 7 dias",
                value = state.avg7PerDay?.let(Formats::decimal1) ?: "—",
                deltaPct = state.avg7DeltaPct,
                caption = if (state.avg7PerDay == null) "sem dias anteriores" else "até ontem",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "intervalo hoje",
                value = state.avgIntervalToday?.let(Formats::duration) ?: "—",
                caption = "média",
                modifier = Modifier.weight(1f),
            )
        }

        Column(Modifier.fillMaxWidth().background(cs.surface, Dimens.cardShape).padding(Dimens.m)) {
            SectionHeader("Por hora · hoje")
            if (state.countToday == 0) {
                EmptyState(if (state.loading) "" else "Ainda sem registos hoje.")
            } else {
                HourlyBars(state.hourly, state.currentHour)
            }
        }
    }

    if (showDebug && debugActions != null) {
        AlertDialog(
            onDismissRequest = { showDebug = false },
            title = { Text("Debug") },
            text = { Text("Gerar dados de teste substitui todos os registos por 90 dias sintéticos.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { showDebug = false; debugActions.seed() }) { Text("Gerar dados de teste") }
                    TextButton(onClick = { showDebug = false; debugActions.clearAll() }) {
                        Text("Apagar tudo", color = cs.error)
                    }
                    TextButton(onClick = { showDebug = false; debugActions.openDesign() }) { Text("Sistema de design") }
                    TextButton(onClick = { showDebug = false }) { Text("Fechar") }
                }
            },
        )
    }
}

/** "último há 1 h 12 min" (last one 1 h 12 min ago), updated every minute while the screen is visible. */
@Composable
private fun lastLabel(last: Instant?, now: () -> Instant): String {
    if (last == null) return "ainda sem registos"
    var current by remember { mutableStateOf(now()) }
    LaunchedEffect(last) {
        while (true) {
            current = now()
            // Aligned to the minute boundary.
            delay(60_000 - current.toEpochMilli() % 60_000)
        }
    }
    val since = Duration.between(last, current)
    return if (since.toMinutes() < 1) "último agora mesmo" else "último há ${Formats.duration(since)}"
}

@Preview(heightDp = 760, backgroundColor = 0xFF1A1B26, showBackground = true)
@Composable
private fun TodayScreenPreview() = CinzeiroTheme {
    Box(Modifier.background(MaterialTheme.colorScheme.background)) {
        TodayScreen(
            state = TodayUiState(
                loading = false,
                date = LocalDate.of(2026, 9, 30),
                countToday = 9,
                lastInstant = Instant.parse("2026-09-30T12:32:00Z"),
                avg7PerDay = 11.4,
                avg7DeltaPct = -8.0,
                avgIntervalToday = Duration.ofMinutes(86),
                hourly = listOf(0, 0, 0, 0, 0, 0, 0, 1, 2, 1, 0, 1, 0, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0),
                currentHour = 14,
            ),
            now = { Instant.parse("2026-09-30T13:44:00Z") },
            onPlusOne = {},
        )
    }
}

@Preview(heightDp = 760, backgroundColor = 0xFF1A1B26, showBackground = true)
@Composable
private fun TodayScreenEmptyPreview() = CinzeiroTheme {
    TodayScreen(
        state = TodayUiState(loading = false, date = LocalDate.of(2026, 9, 30)),
        now = { Instant.parse("2026-09-30T13:44:00Z") },
        onPlusOne = {},
    )
}
