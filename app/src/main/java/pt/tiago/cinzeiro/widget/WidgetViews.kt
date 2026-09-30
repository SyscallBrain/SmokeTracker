package pt.tiago.cinzeiro.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import pt.tiago.cinzeiro.MainActivity
import pt.tiago.cinzeiro.R
import java.time.LocalDate

/** Textos variáveis do widget (o «hoje» é fixo no layout). */
data class WidgetTexts(val count: String, val last: String)

/** Conteúdo do widget a partir do estado (função pura, testada em JVM). */
fun widgetTexts(state: WidgetState, today: LocalDate = state.day): WidgetTexts {
    val shown = state.displayedOn(today)
    return WidgetTexts(shown.todayCount.toString(), shown.lastLabel)
}

/** Constrói as `RemoteViews` do widget (R5): layout `widget_smoke`, textos e cliques. */
object WidgetViews {
    private const val REQUEST_LOG = 1
    private const val REQUEST_OPEN = 2

    fun build(context: Context, state: WidgetState): RemoteViews {
        val texts = widgetTexts(state)
        return RemoteViews(context.packageName, R.layout.widget_smoke).apply {
            setTextViewText(R.id.widget_count, texts.count)
            setTextViewText(R.id.widget_last, texts.last)
            setOnClickPendingIntent(android.R.id.background, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_log, logIntent(context))
        }
    }

    /** «+1»: broadcast explícito para [CinzeiroWidgetReceiver], acção [CinzeiroWidgetReceiver.ACTION_LOG]. */
    fun logIntent(context: Context): PendingIntent {
        val intent = Intent(context, CinzeiroWidgetReceiver::class.java).setAction(CinzeiroWidgetReceiver.ACTION_LOG)
        return PendingIntent.getBroadcast(
            context, REQUEST_LOG, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Toque no resto do widget: abre a app. */
    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, REQUEST_OPEN, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
