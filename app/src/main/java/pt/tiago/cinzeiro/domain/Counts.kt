package pt.tiago.cinzeiro.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class DayCount(val date: LocalDate, val count: Int)

data class WeekCount(val weekStart: LocalDate, val count: Int)

/** Count per local day, from [from] to [to] inclusive, with days without events at 0. */
fun dailyCounts(events: List<SmokeEvent>, from: LocalDate, to: LocalDate): List<DayCount> {
    if (to < from) return emptyList()
    val size = (to.toEpochDay() - from.toEpochDay() + 1).toInt()
    val counts = IntArray(size)
    val fromDay = from.toEpochDay()
    for (e in events) {
        val idx = e.localDate().toEpochDay() - fromDay
        if (idx in 0 until size) counts[idx.toInt()]++
    }
    return List(size) { DayCount(from.plusDays(it.toLong()), counts[it]) }
}

/** Count per week (starting on Monday) of the events from [from] to [to] inclusive. */
fun weeklyCounts(events: List<SmokeEvent>, from: LocalDate, to: LocalDate): List<WeekCount> =
    dailyCounts(events, from, to)
        .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .map { (weekStart, days) -> WeekCount(weekStart, days.sumOf { it.count }) }
