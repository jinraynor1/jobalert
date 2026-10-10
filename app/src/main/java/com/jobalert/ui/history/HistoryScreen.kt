package com.jobalert.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jobalert.JobAlertApp
import com.jobalert.domain.model.Alert
import com.jobalert.ui.theme.LocalAlertColor
import com.jobalert.ui.theme.LocalSuccessColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** Filas de la lista: un encabezado por día seguido de sus alertas. */
private sealed interface HistoryRow {
    data class Day(val label: String) : HistoryRow
    data class Item(val alert: Alert) : HistoryRow
}

private fun toRows(alerts: List<Alert>): List<HistoryRow> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val rows = mutableListOf<HistoryRow>()
    var currentDay: LocalDate? = null
    for (alert in alerts) {
        val day = Instant.ofEpochMilli(alert.timestamp).atZone(zone).toLocalDate()
        if (day != currentDay) {
            currentDay = day
            val label = when (day) {
                today -> "Hoy"
                today.minusDays(1) -> "Ayer"
                else -> dayFormat.format(day).replaceFirstChar { it.uppercase() }
            }
            rows += HistoryRow.Day(label)
        }
        rows += HistoryRow.Item(alert)
    }
    return rows
}

private fun formatTime(timestamp: Long): String {
    val minutes = (System.currentTimeMillis() - timestamp) / 60_000
    return when {
        minutes < 1 -> "ahora"
        minutes < 60 -> "hace $minutes min"
        else -> timeFormat.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as JobAlertApp
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory { initializer { HistoryViewModel(app.alertRepository) } }
    )

    val alerts by viewModel.alerts.collectAsState()
    val rows = remember(alerts) { toRows(alerts) }
    val pending = alerts.count { !it.acknowledged }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showAcknowledgeAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (alerts.isNotEmpty()) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Historial", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (pending == 0) "Todo atendido"
                                else if (pending == 1) "1 pendiente"
                                else "$pending pendientes",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (pending == 0) LocalSuccessColor.current else LocalAlertColor.current
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.addTestAlert() }) {
                            Icon(Icons.Default.AddAlert, contentDescription = "Generar alerta de prueba")
                        }
                        if (pending > 0) {
                            IconButton(onClick = { showAcknowledgeAllDialog = true }) {
                                Icon(Icons.Default.DoneAll, contentDescription = "Marcar todas como atendidas")
                            }
                        }
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Borrar todo")
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (alerts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.NotificationsNone,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text("Sin alertas todavía", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cuando un correo coincida con una regla, aparecerá aquí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.addTestAlert() }) {
                    Text("Generar alerta de prueba")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    rows,
                    key = { row ->
                        when (row) {
                            is HistoryRow.Day -> "day-${row.label}"
                            is HistoryRow.Item -> row.alert.id
                        }
                    }
                ) { row ->
                    when (row) {
                        is HistoryRow.Day -> Text(
                            row.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 0.dp)
                        )
                        is HistoryRow.Item -> AlertHistoryCard(
                            alert = row.alert,
                            onAcknowledge = { viewModel.acknowledge(row.alert.id) },
                            onUnacknowledge = { viewModel.unacknowledge(row.alert.id) },
                            onDelete = { viewModel.deleteAlert(row.alert.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAcknowledgeAllDialog) {
        AlertDialog(
            onDismissRequest = { showAcknowledgeAllDialog = false },
            title = { Text("Marcar como atendidas") },
            text = { Text("¿Marcar las $pending alertas pendientes como atendidas?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acknowledgeAll()
                    showAcknowledgeAllDialog = false
                }) {
                    Text("Marcar todas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAcknowledgeAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Borrar historial") },
            text = { Text("¿Eliminar las ${alerts.size} alertas del historial? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showDeleteAllDialog = false
                }) {
                    Text("Borrar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun AlertHistoryCard(
    alert: Alert,
    onAcknowledge: () -> Unit,
    onUnacknowledge: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hasBody = alert.snippet.isNotBlank() && alert.snippet != alert.subject
    // Solo se puede expandir si el texto colapsado quedó cortado; se mide únicamente
    // mientras está colapsado, así el resultado se conserva al expandir.
    var subjectCut by remember { mutableStateOf(false) }
    var bodyCut by remember { mutableStateOf(false) }
    val canExpand = subjectCut || bodyCut
    val pending = !alert.acknowledged

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar alerta") },
            text = { Text("¿Eliminar esta alerta del historial?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (pending) 1f else 0.7f)
            .animateContentSize(),
        onClick = { if (canExpand) expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = if (pending) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // La franja lateral es la única marca de "pendiente".
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(if (pending) LocalAlertColor.current else MaterialTheme.colorScheme.surfaceContainerLow)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (alert.ruleName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.MailOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    alert.ruleName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatTime(alert.timestamp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar alerta",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    alert.subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (pending) FontWeight.Bold else FontWeight.Normal,
                    color = if (pending) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) subjectCut = it.hasVisualOverflow }
                )
                Text(
                    alert.sender,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasBody) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        alert.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) bodyCut = it.hasVisualOverflow }
                    )
                }
                if (canExpand) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Ver menos" else "Ver más",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pending) {
                        FilledTonalButton(
                            onClick = onAcknowledge,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Marcar como atendida", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = LocalSuccessColor.current
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Atendida",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalSuccessColor.current
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = onUnacknowledge,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Marcar como pendiente", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
