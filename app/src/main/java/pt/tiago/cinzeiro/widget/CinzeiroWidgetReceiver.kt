package pt.tiago.cinzeiro.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class CinzeiroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CinzeiroWidget()

    /** Primeiro widget colocado: agenda a actualização da meia-noite. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightWorker.schedule(context)
    }

    /** Último widget removido: deixa de acordar à meia-noite. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightWorker.cancel(context)
    }
}
