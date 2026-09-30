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
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import pt.tiago.cinzeiro.App
import pt.tiago.cinzeiro.MainActivity
import pt.tiago.cinzeiro.feedback.FeedbackLog
import java.time.LocalDate

/**
 * Widget como **vista de estado empurrado** (R4). O estado vive no `DataStore<Preferences>` do
 * Glance (um por widget, `PreferencesGlanceStateDefinition`) e é escrito por [WidgetUpdater.push];
 * a composição só o lê com `currentState<Preferences>()`. Não há Flows do Room na composição.
 *
 * Porque funciona com sessão viva ou morta (Glance 1.2.0, fontes):
 * - sessão viva: `update()` envia `UpdateGlanceState`; `AppWidgetSession.processEvent` relê o
 *   estado com `configManager.getValue(...)` e atribui-o a `glanceState`, que é o valor de
 *   `LocalState` → recompõe com o valor novo;
 * - sessão nova: `AppWidgetSession.provideGlance` lê o estado antes da primeira composição.
 */
class CinzeiroWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as App
        val repo = app.container.repository
        // Widget acabado de pôr (ou dados da app limpos): ainda sem push → preenche uma vez aqui.
        val pushed = WidgetState.readFrom(getAppWidgetState<Preferences>(context, id))
        val seeded = if (pushed == null) {
            val state = loadWidgetState(repo.now(), repo.zone(), repo::countBetween, repo::lastEvent)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply { state.writeTo(this) }
            }
            state
        } else null
        runCatching {
            FeedbackLog.append(app, FeedbackLog.provideLine(id.toString(), pushed ?: seeded, seededNow = seeded != null))
        }
        provideContent {
            val today = LocalDate.now(repo.zone())
            val state = WidgetState.readFrom(currentState<Preferences>())
                ?: seeded
                ?: WidgetState(0, "sem registos", today)
            WidgetContent(state.displayedOn(today))
        }
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
