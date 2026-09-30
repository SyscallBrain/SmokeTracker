package pt.tiago.cinzeiro.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Põe a contagem do widget a zeros na mudança de dia: redesenha e reagenda-se para a meia-noite
 * local seguinte (one-off encadeado; `updatePeriodMillis` não desce dos 30 min). Deixa de se
 * reagendar quando já não há widgets no ecrã inicial.
 */
class MidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.push(applicationContext, PushCaller.MIDNIGHT)
        if (hasWidgets(applicationContext)) schedule(applicationContext, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "cinzeiro-widget-midnight"

        /** Margem para não acordar uns milissegundos antes da meia-noite e desenhar ainda «ontem». */
        private const val MARGIN_MS = 5_000L

        suspend fun hasWidgets(context: Context): Boolean =
            GlanceAppWidgetManager(context).getGlanceIds(CinzeiroWidget::class.java).isNotEmpty()

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
