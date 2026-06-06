package com.jobalert.ui.accounts

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jobalert.JobAlertApp
import com.jobalert.data.model.EmailAccount
import com.jobalert.oauth.OAuthConfig
import com.jobalert.ui.theme.LocalSuccessColor
import kotlinx.coroutines.launch

private const val GOOGLE_HOST = "imap.gmail.com"
private const val MICROSOFT_HOST = "outlook.office365.com"
private const val DEFAULT_PORT = 993

@Composable
fun AccountsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as JobAlertApp
    val viewModel: AccountsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AccountsViewModel(app.emailAccountRepository, app.credentialStore) }
        }
    )

    val accounts by viewModel.accounts.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<EmailAccount?>(null) }

    // Captured before launching OAuth so the result handler knows what account to save
    var pendingOAuthType by remember { mutableStateOf("") }
    var pendingOAuthEmail by remember { mutableStateOf("") }
    var pendingOAuthHost by remember { mutableStateOf("") }
    var pendingOAuthPort by remember { mutableStateOf(DEFAULT_PORT) }
    var pendingOAuthUseSsl by remember { mutableStateOf(true) }
    var pendingOAuthConfig by remember { mutableStateOf<OAuthConfig?>(null) }
    var pendingReauthAccount by remember { mutableStateOf<EmailAccount?>(null) }

    val oauthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val reauthTarget = pendingReauthAccount
        if (reauthTarget != null) {
            viewModel.reauthorizeOAuthAccount(reauthTarget, result.data, pendingOAuthConfig, context)
            pendingReauthAccount = null
        } else {
            viewModel.handleOAuthResult(
                data = result.data,
                authType = pendingOAuthType,
                email = pendingOAuthEmail,
                host = pendingOAuthHost,
                port = pendingOAuthPort,
                useSsl = pendingOAuthUseSsl,
                oauthConfig = pendingOAuthConfig,
                context = context
            )
        }
    }

    fun launchOAuth(authType: String, email: String, host: String, port: Int, useSsl: Boolean, oauthConfig: OAuthConfig?) {
        pendingOAuthType = authType
        pendingOAuthEmail = email
        pendingOAuthHost = host
        pendingOAuthPort = port
        pendingOAuthUseSsl = useSsl
        pendingOAuthConfig = oauthConfig
        scope.launch {
            val intent = viewModel.buildOAuthIntent(authType, oauthConfig, context) ?: return@launch
            oauthLauncher.launch(intent)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva cuenta")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- Scan now row ---
            val scanState by viewModel.scanState.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (val st = scanState) {
                    is ScanState.Running -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Escaneando…")
                        }
                    }
                    else -> {
                        Button(
                            onClick = { viewModel.scanNow(app) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Escanear ahora")
                        }
                        if (st is ScanState.Done) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Listo · ${st.newCount} nuevos",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalSuccessColor.current
                            )
                        }
                    }
                }
            }

            // --- Account list or empty state ---
            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sin cuentas. Toca + para agregar una.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            onToggle = { viewModel.setAccountEnabled(account.id, it) },
                            onEdit = { editingAccount = account },
                            onDelete = { viewModel.deleteAccount(account) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AccountDialog(
            initialAccount = null,
            credentialStore = app.credentialStore,
            onConfirmPassword = { email, password, host, port, useSsl ->
                viewModel.addAccount(email, password, host, port, useSsl, "PASSWORD")
                showAddDialog = false
            },
            onLaunchOAuth = { authType, email, host, port, useSsl, oauthConfig ->
                showAddDialog = false
                launchOAuth(authType, email, host, port, useSsl, oauthConfig)
            },
            onDismiss = { showAddDialog = false },
            onTestConnection = { host, port, useSsl, email, credential, authType ->
                viewModel.testConnection(host, port, useSsl, email, credential, authType)
            }
        )
    }

    editingAccount?.let { account ->
        if (account.authType == "PASSWORD") {
            AccountDialog(
                initialAccount = account,
                credentialStore = app.credentialStore,
                onConfirmPassword = { email, password, host, port, useSsl ->
                    viewModel.updateAccount(account, email, password, host, port, useSsl)
                    editingAccount = null
                },
                onLaunchOAuth = { authType, email, host, port, useSsl, oauthConfig ->
                    editingAccount = null
                    launchOAuth(authType, email, host, port, useSsl, oauthConfig)
                },
                onDismiss = { editingAccount = null },
                onTestConnection = { host, port, useSsl, email, credential, authType ->
                    viewModel.testConnection(host, port, useSsl, email, credential, authType)
                }
            )
        } else {
            ReauthDialog(
                account = account,
                onReauth = {
                    pendingReauthAccount = account
                    pendingOAuthConfig = null
                    editingAccount = null
                    scope.launch {
                        val intent = viewModel.buildOAuthIntent(account.authType, null, context) ?: return@launch
                        oauthLauncher.launch(intent)
                    }
                },
                onDismiss = { editingAccount = null }
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: EmailAccount,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account.authType != "PASSWORD") {
                val isInvalid = account.needsReauth
                Icon(
                    imageVector = if (isInvalid) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = if (isInvalid) "Token inválido, requiere re-autorización" else "Token válido",
                    tint = if (isInvalid) MaterialTheme.colorScheme.error else LocalSuccessColor.current,
                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(account.email, style = MaterialTheme.typography.titleSmall)
                val subtitle = when (account.authType) {
                    "OAUTH2_GOOGLE" -> "Google OAuth 2.0"
                    "OAUTH2_MICROSOFT" -> "Microsoft OAuth 2.0"
                    "OAUTH2_CUSTOM" -> "OAuth personalizado · ${account.host}:${account.port}"
                    else -> "${account.host}:${account.port}${if (account.useSsl) " (SSL)" else ""}"
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = account.isEnabled, onCheckedChange = onToggle, modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
        }
    }
}

@Composable
private fun ReauthDialog(account: EmailAccount, onReauth: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cuenta OAuth") },
        text = {
            Column {
                Text(account.email, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Para actualizar el acceso, re-autoriza la cuenta. El correo y proveedor no cambian.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onReauth) { Text("Re-autorizar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AccountDialog(
    initialAccount: EmailAccount?,
    credentialStore: com.jobalert.data.repository.CredentialStore,
    onConfirmPassword: (email: String, password: String?, host: String, port: Int, useSsl: Boolean) -> Unit,
    onLaunchOAuth: (authType: String, email: String, host: String, port: Int, useSsl: Boolean, oauthConfig: OAuthConfig?) -> Unit,
    onDismiss: () -> Unit,
    onTestConnection: suspend (host: String, port: Int, useSsl: Boolean, email: String, credential: String, authType: String) -> Result<Unit>
) {
    val isEdit = initialAccount != null
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf(initialAccount?.email ?: "") }

    // Password section
    var pwExpanded by remember { mutableStateOf(isEdit) }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(initialAccount?.host ?: "") }
    var portText by remember { mutableStateOf(initialAccount?.port?.toString() ?: "993") }
    var useSsl by remember { mutableStateOf(initialAccount?.useSsl ?: true) }

    // Custom OAuth section
    var oauthExpanded by remember { mutableStateOf(false) }
    var authEndpoint by remember { mutableStateOf("") }
    var tokenEndpoint by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var oauthScope by remember { mutableStateOf("") }
    var oauthHost by remember { mutableStateOf("") }
    var oauthPortText by remember { mutableStateOf("993") }
    var oauthUseSsl by remember { mutableStateOf(true) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Editar cuenta" else "Nueva cuenta") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electronico") },
                    placeholder = { Text("usuario@dominio.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isEdit
                )

                if (!isEdit) {
                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = { onLaunchOAuth("OAUTH2_GOOGLE", email.trim(), GOOGLE_HOST, DEFAULT_PORT, true, null) },
                        enabled = email.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Conectar con Gmail (Google)") }

                    Button(
                        onClick = { onLaunchOAuth("OAUTH2_MICROSOFT", email.trim(), MICROSOFT_HOST, DEFAULT_PORT, true, null) },
                        enabled = email.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Conectar con Microsoft 365") }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    TextButton(
                        onClick = { oauthExpanded = !oauthExpanded; if (oauthExpanded) pwExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (oauthExpanded) "▼ OAuth personalizado" else "▶ OAuth personalizado") }

                    if (oauthExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = authEndpoint, onValueChange = { authEndpoint = it },
                                label = { Text("Auth endpoint URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = tokenEndpoint, onValueChange = { tokenEndpoint = it },
                                label = { Text("Token endpoint URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = clientId, onValueChange = { clientId = it },
                                label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = oauthScope, onValueChange = { oauthScope = it },
                                label = { Text("Scope") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = oauthHost, onValueChange = { oauthHost = it },
                                label = { Text("Servidor IMAP (host)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = oauthPortText,
                                    onValueChange = { oauthPortText = it.filter { c -> c.isDigit() } },
                                    label = { Text("Puerto") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("SSL", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = oauthUseSsl, onCheckedChange = { oauthUseSsl = it })
                                }
                            }
                            val customReady = email.isNotBlank() && authEndpoint.isNotBlank() &&
                                    tokenEndpoint.isNotBlank() && clientId.isNotBlank() && oauthHost.isNotBlank()
                            Button(
                                onClick = {
                                    if (customReady) onLaunchOAuth(
                                        "OAUTH2_CUSTOM", email.trim(),
                                        oauthHost.trim(), oauthPortText.toIntOrNull() ?: DEFAULT_PORT, oauthUseSsl,
                                        OAuthConfig(authEndpoint.trim(), tokenEndpoint.trim(), clientId.trim(),
                                            oauthScope.trim(), oauthHost.trim(),
                                            oauthPortText.toIntOrNull() ?: DEFAULT_PORT, oauthUseSsl)
                                    )
                                },
                                enabled = customReady,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Autorizar →") }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    TextButton(
                        onClick = { pwExpanded = !pwExpanded; if (pwExpanded) oauthExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (pwExpanded) "▼ Contrasena IMAP" else "▶ Contrasena IMAP") }
                }

                if (pwExpanded || isEdit) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text(if (isEdit) "Contrasena (vacio = no cambiar)" else "Contrasena") },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = host, onValueChange = { host = it },
                            label = { Text("Servidor IMAP (host)") }, placeholder = { Text("imap.gmail.com") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Text("Gmail: imap.gmail.com:993 (requiere app-password)", style = MaterialTheme.typography.bodySmall)
                        Text("Outlook/M365: outlook.office365.com:993", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = portText, onValueChange = { portText = it.filter { c -> c.isDigit() } },
                                label = { Text("Puerto") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SSL", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = useSsl, onCheckedChange = { useSsl = it })
                            }
                        }
                        val effectivePassword = if (isEdit && password.isBlank()) {
                            credentialStore.getPassword(initialAccount!!.email) ?: ""
                        } else password
                        OutlinedButton(
                            onClick = {
                                isTesting = true
                                testStatus = "Probando conexion..."
                                scope.launch {
                                    val port = portText.toIntOrNull() ?: DEFAULT_PORT
                                    val result = onTestConnection(host, port, useSsl, email, effectivePassword, "PASSWORD")
                                    testStatus = if (result.isSuccess) "Conexion exitosa"
                                    else "Error: ${result.exceptionOrNull()?.message ?: "Error desconocido"}"
                                    isTesting = false
                                }
                            },
                            enabled = !isTesting && email.isNotBlank() && host.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Probar conexion") }
                        testStatus?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.startsWith("Conexion exitosa")) LocalSuccessColor.current else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pwExpanded || isEdit) {
                TextButton(
                    onClick = {
                        val port = portText.toIntOrNull() ?: DEFAULT_PORT
                        val pwd = if (isEdit && password.isBlank()) null else password
                        onConfirmPassword(email.trim(), pwd, host.trim(), port, useSsl)
                    },
                    enabled = email.isNotBlank() && host.isNotBlank() && (isEdit || password.isNotBlank())
                ) { Text(if (isEdit) "Guardar" else "Agregar") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
