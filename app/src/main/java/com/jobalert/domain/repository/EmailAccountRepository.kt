package com.jobalert.domain.repository

import com.jobalert.domain.model.EmailAccount
import kotlinx.coroutines.flow.Flow

interface EmailAccountRepository {
    val allAccounts: Flow<List<EmailAccount>>
    suspend fun getEnabledAccountsOnce(): List<EmailAccount>
    suspend fun insert(account: EmailAccount, password: String?): Long
    suspend fun update(account: EmailAccount, password: String?)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun delete(account: EmailAccount)
    suspend fun updateUidState(id: Long, lastSeenUid: Long, uidValidity: Long)
    suspend fun setNeedsReauth(id: Long, value: Boolean)
}
