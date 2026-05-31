package com.jobalert.imap

import android.util.Log
import com.jobalert.data.model.EmailAccount
import com.jobalert.domain.NotificationData
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Folder
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart

private const val TAG = "JobAlert-IMAP"
private const val SNIPPET_MAX_CHARS = 500

data class FetchResult(
    val messages: List<NotificationData>,
    val newLastSeenUid: Long,
    val uidValidity: Long,
    val error: String? = null
)

object ImapClient {

    suspend fun fetchNew(account: EmailAccount, credential: String): FetchResult =
        withContext(Dispatchers.IO) {
            if (account.authType != "PASSWORD") {
                return@withContext ImapOAuthClient.fetchNew(account, credential)
            }
            var store: javax.mail.Store? = null
            var folder: Folder? = null
            try {
                val session = Session.getInstance(buildSessionProps(account))
                store = session.getStore(if (account.useSsl) "imaps" else "imap")
                store.connect(account.host, account.port, account.email, credential)

                folder = store.getFolder("INBOX")
                folder.open(Folder.READ_ONLY)

                val imapFolder = folder as IMAPFolder
                val currentUidValidity = imapFolder.uidValidity

                if (account.uidValidity != 0L && currentUidValidity != account.uidValidity) {
                    Log.w(TAG, "[${account.email}] UID validity changed — resetting baseline")
                    val highestUid = imapFolder.uidNext - 1
                    return@withContext FetchResult(emptyList(), highestUid, currentUidValidity)
                }

                if (account.uidValidity == 0L) {
                    val highestUid = imapFolder.uidNext - 1
                    Log.i(TAG, "[${account.email}] First run — baseline UID=$highestUid")
                    return@withContext FetchResult(emptyList(), highestUid, currentUidValidity)
                }

                val rawMessages = imapFolder.getMessagesByUID(account.lastSeenUid + 1, IMAPFolder.LASTUID)
                var maxUid = account.lastSeenUid
                val results = mutableListOf<NotificationData>()

                for (msg in rawMessages) {
                    val uid = imapFolder.getUID(msg)
                    if (uid <= account.lastSeenUid) continue
                    if (uid > maxUid) maxUid = uid
                    try {
                        val data = messageToNotificationData(msg)
                        results.add(data)
                        Log.i(TAG, "[${account.email}] New message uid=$uid from=${data.sender}")
                    } catch (e: Exception) {
                        Log.w(TAG, "[${account.email}] Could not parse message uid=$uid: ${e.message}")
                    }
                }

                FetchResult(results, maxUid, currentUidValidity)
            } catch (e: javax.mail.AuthenticationFailedException) {
                Log.e(TAG, "[${account.email}] Authentication failed: ${e.message}")
                FetchResult(emptyList(), account.lastSeenUid, account.uidValidity, "Credenciales incorrectas")
            } catch (e: MessagingException) {
                Log.e(TAG, "[${account.email}] IMAP error: ${e.message}")
                FetchResult(emptyList(), account.lastSeenUid, account.uidValidity, e.message)
            } catch (e: Exception) {
                Log.e(TAG, "[${account.email}] Unexpected error: ${e.message}")
                FetchResult(emptyList(), account.lastSeenUid, account.uidValidity, e.message)
            } finally {
                try { folder?.close(false) } catch (_: Exception) {}
                try { store?.close() } catch (_: Exception) {}
            }
        }

    suspend fun testConnection(
        host: String,
        port: Int,
        useSsl: Boolean,
        email: String,
        credential: String,
        authType: String = "PASSWORD"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var store: javax.mail.Store? = null
        try {
            val dummy = EmailAccount(email = email, host = host, port = port, useSsl = useSsl, authType = authType)
            val session = Session.getInstance(buildSessionProps(dummy))
            store = session.getStore(if (useSsl) "imaps" else "imap")
            store.connect(host, port, email, credential)
            Result.success(Unit)
        } catch (e: javax.mail.AuthenticationFailedException) {
            Result.failure(Exception("Credenciales incorrectas"))
        } catch (e: MessagingException) {
            Result.failure(Exception("Error de conexión: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { store?.close() } catch (_: Exception) {}
        }
    }

    internal fun buildSessionProps(account: EmailAccount): Properties =
        Properties().apply {
            put("mail.store.protocol", if (account.useSsl) "imaps" else "imap")
            put("mail.imaps.host", account.host)
            put("mail.imaps.port", account.port.toString())
            put("mail.imaps.ssl.enable", account.useSsl.toString())
            put("mail.imap.host", account.host)
            put("mail.imap.port", account.port.toString())
            put("mail.imap.ssl.enable", account.useSsl.toString())
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "15000")
            put("mail.imap.connectiontimeout", "15000")
            put("mail.imap.timeout", "15000")
        }

    fun messageToNotificationData(msg: javax.mail.Message): NotificationData {
        val from = msg.from?.firstOrNull()?.let {
            if (it is InternetAddress) it.personal?.takeIf { p -> p.isNotBlank() } ?: it.address
            else it.toString()
        } ?: ""
        val subject = msg.subject?.trim() ?: ""
        val snippet = extractTextSnippet(msg).take(SNIPPET_MAX_CHARS)
        return NotificationData(sender = from, subject = subject, snippet = snippet)
    }

    private fun extractTextSnippet(msg: javax.mail.Message): String {
        return try {
            when (val content = msg.content) {
                is String -> content
                is MimeMultipart -> extractFromMultipart(content)
                else -> ""
            }
        } catch (e: Exception) { "" }
    }

    private fun extractFromMultipart(mp: MimeMultipart): String {
        for (i in 0 until mp.count) {
            val part = mp.getBodyPart(i)
            val ct = part.contentType.lowercase()
            if (ct.startsWith("text/plain")) return part.content as? String ?: ""
            if (ct.startsWith("multipart/")) {
                val nested = part.content
                if (nested is MimeMultipart) {
                    val result = extractFromMultipart(nested)
                    if (result.isNotBlank()) return result
                }
            }
        }
        for (i in 0 until mp.count) {
            val part = mp.getBodyPart(i)
            if (part.contentType.lowercase().startsWith("text/html")) {
                val html = part.content as? String ?: ""
                return html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            }
        }
        return ""
    }
}
