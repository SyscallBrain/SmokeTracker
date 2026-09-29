package pt.tiago.cinzeiro.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import pt.tiago.cinzeiro.App
import pt.tiago.cinzeiro.MainActivity
import pt.tiago.cinzeiro.ui.format.Formats
import java.time.LocalDate
import java.time.ZoneId

/** O que o widget mostra; calculado em [load], desenhado em [WidgetContent]. */
data class WidgetState(val todayCount: Int, val lastLabel: String)

/**
 * «Último» em hora **absoluta** (ADR §6: o widget não se actualiza ao minuto, «há X min» ficaria
 * errado). Hoje → «último 13:32»; outro dia → «último 28 set»; sem registos → «sem registos».
 */
fun lastLabel(last: java.time.Instant?, lastZone: ZoneId?, today: LocalDate, zone: ZoneId): String {
    if (last == null) return "sem registos"
    val local = last.atZone(lastZone ?: zone)
    return if (last.atZone(zone).toLocalDate() == today) "último ${Formats.time(local.toLocalTime())}"
    else "último ${Formats.dayMonth(local.toLocalDate())}"
}

class CinzeiroWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { WidgetContent(state) }
    }

    private suspend fun load(context: Context): WidgetState {
        val repo = (context.applicationContext as App).container.repository
        val zone = repo.zone()
        val today = repo.now().atZone(zone).toLocalDate()
        val from = today.atStartOfDay(zone).toInstant()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant()
        val count = repo.countBetween(from, to)
        val last = repo.observeLast().first()
        return WidgetState(count, lastLabel(last?.instant, last?.zone, today, zone))
    }
}

// Paleta fixa do tema escuro (contrato de design) — o widget não segue as cores dinâmicas.
private val WSurface = ColorProvider(Color(0xFF24283B))
private val WOnSurface = ColorProvider(Color(0xFFC0CAF5))
private val WOnSurfaceVariant = ColorProvider(Color(0xFF737AA2))
private val WPrimary = ColorProvider(Color(0xFFFF9E64))
private val WOnPrimary = ColorProvider(Color(0xFF1A1B26))

@Composable
private fun WidgetContent(state: WidgetState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WSurface)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.todayCount.toString(),
                    style = TextStyle(color = WOnSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text("hoje", style = TextStyle(color = WOnSurfaceVariant, fontSize = 13.sp))
            }
            Text(state.lastLabel, style = TextStyle(color = WOnSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
        Box(
            modifier = GlanceModifier
                .size(52.dp)
                .background(WPrimary)
                .cornerRadius(26.dp)
                .clickable(actionRunCallback<LogCigaretteAction>())
                .semantics { contentDescription = "Registar um cigarro" },
            contentAlignment = Alignment.Center,
        ) {
            Text("+1", style = TextStyle(color = WOnPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
        }
    }
}
