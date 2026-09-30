package pt.tiago.cinzeiro.domain

/** Sum of events per local hour (index = hour 0..23). */
fun hourlyDistribution(events: List<SmokeEvent>): IntArray {
    val hours = IntArray(24)
    for (e in events) hours[e.localHour()]++
    return hours
}

/** Day-of-week × local-hour grid; index 0 = Monday, 6 = Sunday. */
fun weekdayHourHeatmap(events: List<SmokeEvent>): Array<IntArray> {
    val grid = Array(7) { IntArray(24) }
    for (e in events) {
        val local = e.instant.atZone(e.zone)
        grid[local.dayOfWeek.value - 1][local.hour]++
    }
    return grid
}

/** Simple moving average; `null` until there are [window] days. */
fun movingAverage(daily: List<DayCount>, window: Int = 7): List<Double?> {
    require(window > 0) { "window must be > 0" }
    var sum = 0L
    return daily.indices.map { i ->
        sum += daily[i].count
        if (i >= window) sum -= daily[i - window].count
        if (i >= window - 1) sum.toDouble() / window else null
    }
}
