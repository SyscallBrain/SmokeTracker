package pt.tiago.cinzeiro.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Resets the widget count to zero when the day changes: redraws and reschedules itself for the next
 * local midnight (chained one-off; `updatePeriodMillis` cannot go below 30 min). Stops
 * rescheduling itself when there are no widgets left on the home screen.
 */
class MidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.push(applicationContext, PushCaller.MIDNIGHT)
        if (hasWidgets(applicationContext)) schedule(applicationContext, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "cinzeiro-widget-midnight"

        /** Margin so it does not wake a few milliseconds before midnight and still draw "yesterday". */
        private const val MARGIN_MS = 5_000L

        fun hasWidgets(context: Context): Boolean = WidgetUpdater.widgetIds(context).isNotEmpty()

        fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val delay = durationUntilNextLocalMidnight(Instant.now(), ZoneId.systemDefault()).toMillis() + MARGIN_MS
            val request = OneTimeWorkRequestBuilder<MidnightWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
