package pt.tiago.cinzeiro.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import pt.tiago.cinzeiro.data.LoggedEvent
import pt.tiago.cinzeiro.ui.format.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * O que o widget mostra. Calculado fora da composição ([loadWidgetState]) e **empurrado** para o
 * estado Glance de cada widget ([WidgetUpdater.push]); a composição só o lê (R4).
 *
 * [day] é o dia local a que [todayCount] se refere, para não mostrar a contagem de ontem se o
 * push da meia-noite se atrasar ([displayedOn]).
 */
data class WidgetState(val todayCount: Int, val lastLabel: String, val day: LocalDate) {

    /** O que mostrar em [today]: este estado, ou contagem 0 se for de outro dia. */
    fun displayedOn(today: LocalDate): WidgetState = if (day == today) this else copy(todayCount = 0)

    /** Escreve nas Preferences do estado Glance do widget. */
    fun writeTo(prefs: MutablePreferences) {
        prefs[WidgetPrefs.COUNT] = todayCount
        prefs[WidgetPrefs.LAST_LABEL] = lastLabel
        prefs[WidgetPrefs.DAY] = day.toString()
    }

    companion object {
        /** Lê do estado Glance; `null` se ainda não houve push (ou se estiver incompleto/corrompido). */
        fun readFrom(prefs: Preferences): WidgetState? {
            val count = prefs[WidgetPrefs.COUNT] ?: return null
            val label = prefs[WidgetPrefs.LAST_LABEL] ?: return null
            val day = prefs[WidgetPrefs.DAY]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            return WidgetState(count, label, day)
        }
    }
}

/** Chaves do estado Glance (`PreferencesGlanceStateDefinition`: um ficheiro DataStore por widget). */
object WidgetPrefs {
    val COUNT = intPreferencesKey("count")
    val LAST_LABEL = stringPreferencesKey("lastLabel")
    val DAY = stringPreferencesKey("day")
}

/**
 * «Último» em hora **absoluta** (ADR §6: o widget não se actualiza ao minuto, «há X min» ficaria
 * errado). Hoje → «último 13:32»; outro dia → «último 28 set»; sem registos → «sem registos».
 */
fun lastLabel(last: Instant?, lastZone: ZoneId?, today: LocalDate, zone: ZoneId): String {
    if (last == null) return "sem registos"
    val local = last.atZone(lastZone ?: zone)
    return if (last.atZone(zone).toLocalDate() == today) "último ${Formats.time(local.toLocalTime())}"
    else "último ${Formats.dayMonth(local.toLocalDate())}"
}

/** Monta o estado do widget (função pura, testada em JVM). */
fun widgetState(count: Int, last: Instant?, lastZone: ZoneId?, today: LocalDate, zone: ZoneId): WidgetState =
    WidgetState(count, lastLabel(last, lastZone, today, zone), today)

/** Intervalo `[meia-noite, meia-noite seguinte)` do dia [date] em [zone]. */
fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
    date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

/**
 * Estado actual com leituras únicas (sem Flows vivos): contagem do dia local de [now] e último
 * registo. Parâmetros como funções para testar sem Room.
 */
suspend fun loadWidgetState(
    now: Instant,
    zone: ZoneId,
    countBetween: suspend (Instant, Instant) -> Int,
    last: suspend () -> LoggedEvent?,
): WidgetState {
    val today = now.atZone(zone).toLocalDate()
    val (from, to) = dayBounds(today, zone)
    val lastEvent = last()
    return widgetState(countBetween(from, to), lastEvent?.instant, lastEvent?.zone, today, zone)
}

/** Texto do Toast do +1 do widget: «Registado às 13:32 · 5 hoje». */
fun loggedToastText(time: LocalTime, todayCount: Int): String =
    "Registado às ${Formats.time(time)} · $todayCount hoje"
