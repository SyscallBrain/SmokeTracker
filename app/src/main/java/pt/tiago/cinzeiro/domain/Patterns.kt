package pt.tiago.cinzeiro.domain

/** Soma de eventos por hora local (índice = hora 0..23). */
fun hourlyDistribution(events: List<SmokeEvent>): IntArray {
    val hours = IntArray(24)
    for (e in events) hours[e.localHour()]++
    return hours
}

/** Grelha dia da semana × hora local; índice 0 = segunda, 6 = domingo. */
fun weekdayHourHeatmap(events: List<SmokeEvent>): Array<IntArray> {
    val grid = Array(7) { IntArray(24) }
    for (e in events) {
        val local = e.instant.atZone(e.zone)
        grid[local.dayOfWeek.value - 1][local.hour]++
    }
    return grid
}

/** Média móvel simples; `null` enquanto ainda não há [window] dias. */
fun movingAverage(daily: List<DayCount>, window: Int = 7): List<Double?> {
    require(window > 0) { "window tem de ser > 0" }
    var sum = 0L
    return daily.indices.map { i ->
        sum += daily[i].count
        if (i >= window) sum -= daily[i - window].count
        if (i >= window - 1) sum.toDouble() / window else null
    }
}
