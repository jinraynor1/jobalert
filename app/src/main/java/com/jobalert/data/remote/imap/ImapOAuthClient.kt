package com.jobalert.data.remote.imap

import android.util.Base64
import android.util.Log
import com.jobalert.domain.model.EmailAccount
import com.jobalert.domain.model.NotificationData
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.net.ssl.SSLSocketFactory

private const val TAG = "JobAlert-ImapOAuth"

// android-mail excludes IMAPSaslAuthenticator (requires javax.security.sasl, absent on Android).
// This client implements XOAUTH2 directly over an SSL socket — no SASL framework needed.
internal object ImapOAuthClient {

    fun fetchNew(account: EmailAccount, accessToken: String, snippetMaxChars: Int = 500): FetchResult {
        var tagN = 0
        fun tag() = "T${++tagN}"

        val socket = try {
            SSLSocketFactory.getDefault().createSocket(account.host, account.port).also {
                it.soTimeout = 15_000
            }
        } catch (e: Exception) {
            Log.e(TAG, "[${account.email}] Connect failed: ${e.message}")
            return FetchResult(emptyList(), account.lastSeenUid, account.uidValidity, e.message)
        }

        val inp = socket.getInputStream()
        val out = socket.getOutputStream()

        fun readLine(): String {
            val sb = StringBuilder()
            while (true) {
                val b = inp.read()
                if (b == -1 || b == '\n'.code) break
                if (b != '\r'.code) sb.append(b.toChar())
            }
            return sb.toString().also { Log.v(TAG, "S: ${it.take(200)}") }
        }

        fun readBytes(n: Int): ByteArray {
            val buf = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = inp.read(buf, off, n - off)
                if (r == -1) break
                off += r
            }
            return buf
        }

        fun send(cmd: String) {
            Log.v(TAG, "C: ${cmd.take(100)}")
            out.write("$cmd\r\n".toByteArray(Charsets.UTF_8))
            out.flush()
        }

        try {
            // Greeting
            if (!readLine().startsWith("* OK")) {
                return FetchResult(emptyList(), account.lastSeenUid, account.uidValidity, "Bad greeting")
            }

            // XOAUTH2 payload: "user=<email>\x01auth=Bearer <token>\x01\x01" base64-encoded.
            // Sent as an initial response in the AUTHENTICATE command (avoids an extra round-trip).
            val xoauthPayload = "user=${account.email}auth=Bearer $accessToken"
            val b64 = Base64.encodeToString(xoauthPayload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val authTag = tag()
            send("$authTag AUTHENTICATE XOAUTH2 $b64")

            var authOk = false
            loop@ while (true) {
                val line = readLine()
                when {
                    line.startsWith("$authTag OK") -> { authOk = true; break@loop }
                    line.startsWith("$authTag NO") || line.startsWith("$authTag BAD") -> {
                        Log.e(TAG, "[${account.email}] Auth rejected: $line")
                        break@loop
                    }
                    // Gmail sends a base64-encoded JSON error detail as a server challenge on failure.
                    // An empty client response aborts the exchange; server then sends the NO.
                    line.startsWith("+ ") -> {
                        runCatching {
                            val detail = String(Base64.decode(line.drop(2), Base64.DEFAULT), Charsets.UTF_8)
                            Log.e(TAG, "[${account.email}] XOAUTH2 server error: $detail")
                        }
                        send("")
                    }
                }
            }

            if (!authOk) {
                return FetchResult(emptyList(), account.lastSeenUid, account.uidValidity, "Credenciales incorrectas")
            }
            Log.i(TAG, "[${account.email}] XOAUTH2 autenticado OK")

            // EXAMINE INBOX (read-only — does not alter message flags)
            val examTag = tag()
            send("$examTag EXAMINE INBOX")
            var uidValidity = account.uidValidity
            var uidNext = 0L
            while (true) {
                val line = readLine()
                Regex("UIDVALIDITY (\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { uidValidity = it }
                Regex("UIDNEXT (\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { uidNext = it }
                if (line.startsWith("$examTag ")) break
            }
            Log.i(TAG, "[${account.email}] INBOX — uidValidity=$uidValidity, uidNext=$uidNext")

            // First run: record current highest UID as baseline; do not fetch any messages.
            if (account.uidValidity == 0L) {
                val baseline = maxOf(0L, uidNext - 1)
                Log.i(TAG, "[${account.email}] First run — baseline UID=$baseline")
                return FetchResult(emptyList(), baseline, uidValidity)
            }

            // UID validity changed: inbox was recreated; reset baseline to avoid re-fetching old mail.
            if (uidValidity != account.uidValidity) {
                val baseline = maxOf(0L, uidNext - 1)
                Log.w(TAG, "[${account.email}] UID validity changed — resetting baseline")
                return FetchResult(emptyList(), baseline, uidValidity)
            }

            // UID SEARCH — list messages with UID > lastSeenUid
            val searchTag = tag()
            send("$searchTag UID SEARCH UID ${account.lastSeenUid + 1}:*")
            var newUids = emptyList<Long>()
            while (true) {
                val line = readLine()
                if (line.startsWith("* SEARCH")) {
                    newUids = line.removePrefix("* SEARCH").trim()
                        .split(" ").mapNotNull { it.toLongOrNull() }
                        .filter { it > account.lastSeenUid }
                }
                if (line.startsWith("$searchTag ")) break
            }
            Log.i(TAG, "[${account.email}] Fetched ${newUids.size} message(s) — lastSeenUid=${account.lastSeenUid}, uidValidity=$uidValidity")

            if (newUids.isEmpty()) {
                return FetchResult(emptyList(), account.lastSeenUid, uidValidity)
            }

            // UID FETCH — retrieve full RFC822 content for each new UID
            val fetchTag = tag()
            send("$fetchTag UID FETCH ${newUids.joinToString(",")} (UID RFC822)")

            val results = mutableListOf<NotificationData>()
            var maxUid = account.lastSeenUid
            val mailSession = Session.getInstance(Properties())
            val expectedUids = newUids.toHashSet()

            while (true) {
                val line = readLine()
                if (line.startsWith("$fetchTag ")) break
                if (!line.startsWith("* ") || !line.contains("FETCH")) continue

                val uid = Regex("UID (\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
                    ?: continue
                if (uid !in expectedUids) continue
                if (uid > maxUid) maxUid = uid

                // Read the RFC822 literal: server announces size as {N} at end of the FETCH line,
                // then sends exactly N bytes followed by the closing ")".
                val literalSize = Regex("RFC822 \\{(\\d+)\\}").find(line)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: continue

                val rawBytes = readBytes(literalSize)
                readLine() // consume closing ")"

                try {
                    val mime = MimeMessage(mailSession, rawBytes.inputStream())
                    val data = ImapClient.messageToNotificationData(mime, snippetMaxChars)
                    results.add(data)
                    Log.i(TAG, "[${account.email}] uid=$uid from=${data.sender} subj=${data.subject}")
                } catch (e: Exception) {
                    Log.w(TAG, "[${account.email}] Parse error uid=$uid: ${e.message}")
                }
            }

            return FetchResult(results, maxUid, uidValidity)
        } finally {
            try { out.write("${tag()} LOGOUT\r\n".toByteArray()); out.flush() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
