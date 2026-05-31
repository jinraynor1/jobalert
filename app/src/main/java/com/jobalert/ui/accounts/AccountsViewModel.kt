package com.jobalert.ui.accounts

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.jobalert.data.model.EmailAccount
import com.jobalert.data.repository.CredentialStore
import com.jobalert.data.repository.EmailAccountRepository
import com.jobalert.imap.ImapClient
import com.jobalert.oauth.OAuthConfig
import com.jobalert.oauth.OAuthTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume

sealed interface ScanState {
    object Idle : ScanState
    object Running : ScanState
    data class Done(val newCount: Int) : ScanState
}

class AccountsViewModel(
    private val repository: EmailAccountRepository,
    private val credentialStore: CredentialStore
) : ViewModel() {

    private val gson = Gson()

    val accounts: StateFlow<List<EmailAccount>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private var scanJob: Job? = null

    // --- Password accounts ---

    fun addAccount(email: String, password: String?, host: String, port: Int, useSsl: Boolean, authType: String = "PASSWORD") {
        viewModelScope.launch {
            repository.insert(
                EmailAccount(email = email, host = host, port = port, useSsl = useSsl, authType = authType),
                password
            )
        }
    }

    fun updateAccount(account: EmailAccount, email: String, password: String?, host: String, port: Int, useSsl: Boolean) {
        viewModelScope.launch {
            repository.update(account.copy(email = email, host = host, port = port, useSsl = useSsl), password)
        }
    }

    fun setAccountEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun deleteAccount(account: EmailAccount) {
        viewModelScope.launch { repository.delete(account) }
    }

    suspend fun testConnection(host: String, port: Int, useSsl: Boolean, email: String, credential: String, authType: String = "PASSWORD"): Result<Unit> {
        return ImapClient.testConnection(host, port, useSsl, email, credential, authType)
    }

    // --- OAuth accounts ---

    // Builds the Custom Tabs intent for the given auth type. Suspend because it fetches
    // the discovery document over the network for Google and Microsoft.
    suspend fun buildOAuthIntent(authType: String, oauthConfig: OAuthConfig?, context: Context): Intent? {
        return OAuthTokenManager.buildAuthorizationIntent(authType, oauthConfig, context)
    }

    // Called when the Custom Tabs flow returns with an authorization code.
    // Exchanges the code for tokens, stores them, and inserts the EmailAccount into Room.
    fun handleOAuthResult(
        data: Intent?,
        authType: String,
        email: String,
        host: String,
        port: Int,
        useSsl: Boolean,
        oauthConfig: OAuthConfig?,
        context: Context
    ) {
        val response = AuthorizationResponse.fromIntent(data ?: return) ?: return
        viewModelScope.launch {
            val service = AuthorizationService(context)
            try {
                val tokenResponse = suspendCancellableCoroutine<TokenResponse?> { cont ->
                    service.performTokenRequest(response.createTokenExchangeRequest()) { resp, _ ->
                        cont.resume(resp)
                    }
                } ?: return@launch

                OAuthTokenManager.storeTokensFromResponse(
                    email = email,
                    accessToken = tokenResponse.accessToken ?: return@launch,
                    refreshToken = tokenResponse.refreshToken ?: return@launch,
                    expirationTime = tokenResponse.accessTokenExpirationTime,
                    credentialStore = credentialStore
                )

                repository.insert(
                    EmailAccount(
                        email = email,
                        host = host,
                        port = port,
                        useSsl = useSsl,
                        authType = authType,
                        oauthConfig = oauthConfig?.let { gson.toJson(it) }
                    ),
                    null
                )
            } finally {
                service.dispose()
            }
        }
    }

    // Re-authorizes an existing OAuth account — replaces tokens without changing the account row.
    fun reauthorizeOAuthAccount(account: EmailAccount, data: Intent?, oauthConfig: OAuthConfig?, context: Context) {
        val response = AuthorizationResponse.fromIntent(data ?: return) ?: return
        viewModelScope.launch {
            val service = AuthorizationService(context)
            try {
                val tokenResponse = suspendCancellableCoroutine<TokenResponse?> { cont ->
                    service.performTokenRequest(response.createTokenExchangeRequest()) { resp, _ ->
                        cont.resume(resp)
                    }
                } ?: return@launch
                OAuthTokenManager.storeTokensFromResponse(
                    email = account.email,
                    accessToken = tokenResponse.accessToken ?: return@launch,
                    refreshToken = tokenResponse.refreshToken ?: return@launch,
                    expirationTime = tokenResponse.accessTokenExpirationTime,
                    credentialStore = credentialStore
                )
            } finally {
                service.dispose()
            }
        }
    }

    // --- Scan now ---

    fun scanNow(app: com.jobalert.JobAlertApp) {
        if (_scanState.value == ScanState.Running) return
        _scanState.value = ScanState.Running
        app.scanNow()
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            androidx.work.WorkManager.getInstance(app)
                .getWorkInfosForUniqueWorkFlow(com.jobalert.JobAlertApp.SCAN_NOW_WORK)
                .collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    when (info.state) {
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            val n = info.outputData.getInt(com.jobalert.work.EmailPollWorker.KEY_NEW_COUNT, 0)
                            _scanState.value = ScanState.Done(n)
                            kotlinx.coroutines.delay(2000)
                            _scanState.value = ScanState.Idle
                            scanJob?.cancel()
                        }
                        androidx.work.WorkInfo.State.FAILED,
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            _scanState.value = ScanState.Done(0)
                            kotlinx.coroutines.delay(2000)
                            _scanState.value = ScanState.Idle
                            scanJob?.cancel()
                        }
                        else -> _scanState.value = ScanState.Running
                    }
                }
        }
    }
}
