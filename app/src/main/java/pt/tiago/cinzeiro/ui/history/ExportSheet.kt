package pt.tiago.cinzeiro.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import pt.tiago.cinzeiro.ui.theme.Dimens

/** Folha «Exportar»: CSV (folha de cálculo) ou JSON (cópia de segurança). Desactivada com a BD vazia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onPick: (ExportFormat) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(bottom = Dimens.xl)) {
            Text(
                "Exportar todos os registos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.s),
            )
            if (!enabled) {
                Text(
                    "Sem registos para exportar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
                )
            }
            Option("CSV (folha de cálculo)", "Abre no Excel/LibreOffice", Icons.Rounded.TableChart, enabled) { onPick(ExportFormat.CSV) }
            Option("JSON (cópia de segurança)", "Formato versionado para repor dados", Icons.Rounded.DataObject, enabled) { onPick(ExportFormat.JSON) }
        }
    }
}

@Composable
private fun Option(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier.semantics { disabled() }),
    )
}
