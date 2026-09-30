package pt.tiago.cinzeiro.widget

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import pt.tiago.cinzeiro.data.LoggedEvent
import pt.tiago.cinzeiro.ui.format.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Monta o estado do widget (função pura, testada em JVM). */
fun widgetState(count: Int, last: Instant?, lastZone: ZoneId?, today: LocalDate, zone: ZoneId): WidgetState =
    WidgetState(count, lastLabel(last, lastZone, today, zone))

/** Intervalo `[meia-noite, meia-noite seguinte)` do dia [date] em [zone]. */
fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
    date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

/** Margem para não acordar uns milissegundos antes da meia-noite e emitir ainda «ontem». */
private const val MIDNIGHT_MARGIN_MS = 1_000L

/**
 * Dia local actual; volta a emitir logo a seguir a cada meia-noite local, para o intervalo da
 * contagem mudar mesmo que a sessão do Glance esteja viva nessa altura.
 */
fun todayFlow(now: () -> Instant, zone: ZoneId): Flow<LocalDate> = flow {
    while (true) {
        val instant = now()
        emit(instant.atZone(zone).toLocalDate())
        delay(durationUntilNextLocalMidnight(instant, zone).toMillis() + MIDNIGHT_MARGIN_MS)
    }
}.distinctUntilChanged()

/**
 * Estado do widget que reemite a cada inserção/remoção (o Room reemite as queries observadas
 * quando a tabela muda) e a cada mudança de dia.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun widgetStateFlow(
    now: () -> Instant,
    zone: ZoneId,
    observeCount: (Instant, Instant) -> Flow<Int>,
    observeLast: () -> Flow<LoggedEvent?>,
): Flow<WidgetState> = todayFlow(now, zone).flatMapLatest { today ->
    val (from, to) = dayBounds(today, zone)
    combine(observeCount(from, to), observeLast()) { count, last ->
        widgetState(count, last?.instant, last?.zone, today, zone)
    }
}.distinctUntilChanged()

/** Texto do Toast do +1 do widget: «Registado às 13:32 · 5 hoje». */
fun loggedToastText(time: LocalTime, todayCount: Int): String =
    "Registado às ${Formats.time(time)} · $todayCount hoje"
