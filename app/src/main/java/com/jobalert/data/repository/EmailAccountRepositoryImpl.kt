package com.jobalert.data.repository

import com.jobalert.data.local.credential.CredentialStore
import com.jobalert.data.local.db.EmailAccountDao
import com.jobalert.data.mapper.toDomain
import com.jobalert.data.mapper.toEntity
import com.jobalert.domain.model.EmailAccount
import com.jobalert.domain.repository.EmailAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EmailAccountRepositoryImpl(
    private val dao: EmailAccountDao,
    private val credentialStore: CredentialStore
) : EmailAccountRepository {

    override val allAccounts: Flow<List<EmailAccount>> =
        dao.getAllAccounts().map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabledAccountsOnce(): List<EmailAccount> =
        dao.getEnabledAccountsOnce().map { it.toDomain() }

    override suspend fun insert(account: EmailAccount, password: String?): Long {
        val id = dao.insert(account.toEntity())
        if (password != null) credentialStore.setPassword(account.email, password)
        return id
    }

    override suspend fun update(account: EmailAccount, password: String?) {
        dao.update(account.toEntity())
        if (password != null) credentialStore.setPassword(account.email, password)
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun delete(account: EmailAccount) {
        dao.delete(account.toEntity())
        credentialStore.removePassword(account.email)
    }

    override suspend fun updateUidState(id: Long, lastSeenUid: Long, uidValidity: Long) =
        dao.updateUidState(id, lastSeenUid, uidValidity)

    override suspend fun setNeedsReauth(id: Long, value: Boolean) =
        dao.setNeedsReauth(id, value)
}
