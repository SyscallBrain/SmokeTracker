package pt.tiago.cinzeiro.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.tiago.cinzeiro.feedback.Feedback
import pt.tiago.cinzeiro.feedback.FeedbackOrigin
import pt.tiago.cinzeiro.settings.Settings
import pt.tiago.cinzeiro.settings.SettingsRepository
import pt.tiago.cinzeiro.settings.VibrationType
import pt.tiago.cinzeiro.ui.components.SectionHeader
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme
import pt.tiago.cinzeiro.ui.theme.Dimens

/** Number of denied requests after which the system stops showing the dialog (Android 11+). */
private const val MAX_PERMISSION_DIALOGS = 2

@Composable
fun SettingsRoute(repo: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val requests by repo.notificationRequests.collectAsStateWithLifecycle(initialValue = 0)
    var notificationsOn by remember { mutableStateOf(Feedback.notificationsEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsOn = Feedback.notificationsEnabled(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = Feedback.notificationsEnabled(context)
    }

    SettingsScreen(
        settings = settings,
        notificationsOn = notificationsOn,
        onBack = onBack,
        onVibrate = { scope.launch { repo.setVibrate(it) } },
        onType = { scope.launch { repo.setVibrationType(it) } },
        onTest = { scope.launch { Feedback.performAndLog(context, settings.copy(vibrate = true), FeedbackOrigin.TEST) } },
        onShowMessage = { scope.launch { repo.setShowMessage(it) } },
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && requests < MAX_PERMISSION_DIALOGS) {
                scope.launch { repo.countNotificationRequest() }
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        },
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
fun SettingsScreen(
    settings: Settings,
    notificationsOn: Boolean,
    onBack: () -> Unit,
    onVibrate: (Boolean) -> Unit,
    onType: (VibrationType) -> Unit,
    onTest: () -> Unit,
    onShowMessage: (Boolean) -> Unit,
    onAllowNotifications: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.s),
        verticalArrangement = Arrangement.spacedBy(Dimens.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar") }
            Text("Definições", style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
        }

        Column(
            Modifier.fillMaxWidth().background(cs.surface, Dimens.cardShape).padding(Dimens.m),
            verticalArrangement = Arrangement.spacedBy(Dimens.m),
        ) {
            SectionHeader("Ao registar")
            SwitchRow("Vibrar ao registar", settings.vibrate, onVibrate)
            Text("Tipo de vibração", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                VibrationType.entries.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = settings.vibrationType == type,
                        onClick = { onType(type) },
                        enabled = settings.vibrate,
                        shape = SegmentedButtonDefaults.itemShape(i, VibrationType.entries.size),
                    ) { Text(type.label) }
                }
            }
            OutlinedButton(onClick = onTest, enabled = settings.vibrate, modifier = Modifier.align(Alignment.End)) {
                Text("Testar")
            }
            SwitchRow("Mostrar mensagem ao registar pelo widget", settings.showMessage, onShowMessage)
        }

        Column(
            Modifier.fillMaxWidth().background(cs.surface, Dimens.cardShape).padding(Dimens.m),
            verticalArrangement = Arrangement.spacedBy(Dimens.s),
        ) {
            SectionHeader("Notificações")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notificações da app: " + if (notificationsOn) "ligadas" else "desligadas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (!notificationsOn) TextButton(onClick = onAllowNotifications) { Text("Permitir") }
            }
            Text(
                "Necessário para a mensagem aparecer quando registas pelo widget.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = cs.secondary),
        )
    }
}

@Preview(heightDp = 700, backgroundColor = 0xFF1A1B26, showBackground = true)
@Composable
private fun SettingsScreenPreview() = CinzeiroTheme {
    SettingsScreen(Settings(vibrationType = VibrationType.DOUBLE), false, {}, {}, {}, {}, {}, {})
}
