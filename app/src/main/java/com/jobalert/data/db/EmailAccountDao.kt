package com.jobalert.data.db

import androidx.room.*
import com.jobalert.data.model.EmailAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailAccountDao {
    @Query("SELECT * FROM email_accounts ORDER BY email ASC")
    fun getAllAccounts(): Flow<List<EmailAccount>>

    @Query("SELECT * FROM email_accounts WHERE isEnabled = 1 ORDER BY email ASC")
    suspend fun getEnabledAccountsOnce(): List<EmailAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: EmailAccount): Long

    @Update
    suspend fun update(account: EmailAccount)

    @Delete
    suspend fun delete(account: EmailAccount)

    @Query("UPDATE email_accounts SET lastSeenUid = :lastSeenUid, uidValidity = :uidValidity WHERE id = :id")
    suspend fun updateUidState(id: Long, lastSeenUid: Long, uidValidity: Long)

    @Query("UPDATE email_accounts SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE email_accounts SET needsReauth = :value WHERE id = :id")
    suspend fun setNeedsReauth(id: Long, value: Boolean)
}
