package com.jobalert.data.local.db

import androidx.room.*
import com.jobalert.data.local.entity.EmailAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailAccountDao {
    @Query("SELECT * FROM email_accounts ORDER BY email ASC")
    fun getAllAccounts(): Flow<List<EmailAccountEntity>>

    @Query("SELECT * FROM email_accounts WHERE isEnabled = 1 ORDER BY email ASC")
    suspend fun getEnabledAccountsOnce(): List<EmailAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: EmailAccountEntity): Long

    @Update
    suspend fun update(account: EmailAccountEntity)

    @Delete
    suspend fun delete(account: EmailAccountEntity)

    @Query("UPDATE email_accounts SET lastSeenUid = :lastSeenUid, uidValidity = :uidValidity WHERE id = :id")
    suspend fun updateUidState(id: Long, lastSeenUid: Long, uidValidity: Long)

    @Query("UPDATE email_accounts SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE email_accounts SET needsReauth = :value WHERE id = :id")
    suspend fun setNeedsReauth(id: Long, value: Boolean)
}
