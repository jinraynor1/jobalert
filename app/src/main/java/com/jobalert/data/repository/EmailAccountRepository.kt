package com.jobalert.data.repository

import com.jobalert.data.db.EmailAccountDao
import com.jobalert.data.model.EmailAccount
import kotlinx.coroutines.flow.Flow

class EmailAccountRepository(
    private val dao: EmailAccountDao,
    private val credentialStore: CredentialStore
) {
    val allAccounts: Flow<List<EmailAccount>> = dao.getAllAccounts()

    suspend fun getEnabledAccountsOnce(): List<EmailAccount> = dao.getEnabledAccountsOnce()

    suspend fun insert(account: EmailAccount, password: String?): Long {
        val id = dao.insert(account)
        if (password != null) credentialStore.setPassword(account.email, password)
        return id
    }

    suspend fun update(account: EmailAccount, password: String?) {
        dao.update(account)
        if (password != null) {
            credentialStore.setPassword(account.email, password)
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(account: EmailAccount) {
        dao.delete(account)
        credentialStore.removePassword(account.email)
    }

    suspend fun updateUidState(id: Long, lastSeenUid: Long, uidValidity: Long) =
        dao.updateUidState(id, lastSeenUid, uidValidity)
}
