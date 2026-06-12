package com.jobalert.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jobalert.JobAlertApp
import com.jobalert.domain.Rule
import com.jobalert.ui.theme.LocalAlertColor

// null = color del tema (rojo por defecto); los demás son ARGB
private val RULE_COLOR_PRESETS: List<Int?> = listOf(
    null,
    0xFFB3261E.toInt(), // Rojo
    0xFFE65100.toInt(), // Naranja
    0xFFF9A825.toInt(), // Ámbar
    0xFF2E7D32.toInt(), // Verde
    0xFF1565C0.toInt(), // Azul
    0xFF6A1B9A.toInt(), // Púrpura
)

@Composable
fun RulesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as JobAlertApp
    val viewModel: RulesViewModel = viewModel(
        factory = viewModelFactory { initializer { RulesViewModel(app.ruleRepository) } }
    )

    val rules by viewModel.rules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    var ruleToDelete by remember { mutableStateOf<Rule?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva regla")
            }
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Sin reglas. Toca + para agregar una.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { viewModel.setRuleEnabled(rule.id, it) },
                        onEdit = { editingRule = rule },
                        onDelete = { ruleToDelete = rule }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        RuleDialog(
            initialRule = null,
            onConfirm = { name, senders, keywords, alertColor ->
                viewModel.addRule(name, senders, keywords, alertColor)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Eliminar regla") },
            text = { Text("¿Eliminar \"${rule.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRule(rule)
                    ruleToDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    editingRule?.let { rule ->
        RuleDialog(
            initialRule = rule,
            onConfirm = { name, senders, keywords, alertColor ->
                viewModel.updateRule(rule, name, senders, keywords, alertColor)
                editingRule = null
            },
            onDismiss = { editingRule = null }
        )
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de color de la regla
            val dotColor = rule.alertColor?.let { Color(it) } ?: LocalAlertColor.current
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall)
                if (rule.senders.isNotEmpty()) {
                    Text(
                        "Remitente: ${rule.senders.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Remitente: cualquiera", style = MaterialTheme.typography.bodySmall)
                }
                if (rule.keywords.isNotEmpty()) {
                    Text(
                        "Keywords: ${rule.keywords.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Keywords: cualquiera", style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar regla")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar regla")
            }
        }
    }
}

@Composable
private fun RuleDialog(
    initialRule: Rule?,
    onConfirm: (name: String, senders: List<String>, keywords: List<String>, alertColor: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var sendersText by remember { mutableStateOf(initialRule?.senders?.joinToString(", ") ?: "") }
    var keywordsText by remember { mutableStateOf(initialRule?.keywords?.joinToString(", ") ?: "") }
    var selectedColor by remember { mutableStateOf(initialRule?.alertColor) }

    val isEdit = initialRule != null
    val themeAlertColor = LocalAlertColor.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Editar regla" else "Nueva regla") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la regla") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sendersText,
                    onValueChange = { sendersText = it },
                    label = { Text("Remitente — nombre o correo (separados por coma)") },
                    placeholder = { Text("ej: Sistema Claro, mailer@claro, Indra") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Gmail muestra el nombre del contacto en la notificación. " +
                        "Usa el nombre tal como aparece, o parte del correo.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    label = { Text("Keywords en asunto/cuerpo (separadas por coma)") },
                    placeholder = { Text("ej: CRITICAL, DOWN, FALLO") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Vacío = coincide con cualquier correo del remitente",
                    style = MaterialTheme.typography.bodySmall
                )

                // Paleta de colores de alerta
                Text("Color de alerta", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RULE_COLOR_PRESETS.forEach { preset ->
                        val swatch = preset?.let { Color(it) } ?: themeAlertColor
                        val isSelected = selectedColor == preset
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = preset }
                        )
                    }
                }
                if (selectedColor == null) {
                    Text("Por defecto (tema)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val senders = sendersText.split(",")
                        .map { it.trim() }.filter { it.isNotEmpty() }
                    val keywords = keywordsText.split(",")
                        .map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(name.trim(), senders, keywords, selectedColor)
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (isEdit) "Guardar" else "Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
