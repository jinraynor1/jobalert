# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

JobAlert is an Android app that polls IMAP mailboxes on a schedule and shows a fullscreen overlay when an incoming message matches user-configured rules. Single-module Gradle project (`app/`), min SDK 26, target SDK 35, Kotlin + Jetpack Compose + Room + WorkManager.

## Commands

```bash
# Build
./gradlew :app:assembleDebug

# Unit tests (JVM, no device required)
./gradlew :app:testDebugUnitTest

# Run a single unit test class
./gradlew :app:testDebugUnitTest --tests "com.jobalert.rules.RuleEngineTest"

# Instrumented tests (requires connected device/emulator)
./gradlew :app:connectedDebugAndroidTest

# Run a single instrumented test class
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jobalert.data.AlertDaoTest

# Logcat — full polling cycle
adb logcat -s JobAlert-Worker,JobAlert-OAuth,JobAlert-ImapOAuth,JobAlert-IMAP
```

## Architecture

All repositories and services are instantiated as `lazy` properties on `JobAlertApp` (manual DI — no Hilt/Koin). ViewModels access them via `(application as JobAlertApp).<property>`.

```
JobAlertApp (Application)
  ├── AppDatabase (Room v4)  ← AlertDao, RuleDao, EmailAccountDao
  ├── AlertRepository / RuleRepository / EmailAccountRepository
  ├── CredentialStore        ← EncryptedSharedPreferences (AES-256-GCM)
  ├── SettingsRepository     ← plain SharedPreferences
  └── OverlayManager         ← TYPE_APPLICATION_OVERLAY via ComposeView

WorkManager
  └── EmailPollWorker (CoroutineWorker)
        ├── ImapClient        ← android-mail, PASSWORD accounts only
        ├── ImapOAuthClient   ← raw SSLSocket, OAuth accounts
        ├── OAuthTokenManager ← AppAuth-Android token refresh
        └── RuleEngine        ← stateless, pure matching

UI (Jetpack Compose, Navigation Compose)
  MainActivity → AppNavigation
    AccountsScreen / RulesScreen / HistoryScreen / SettingsScreen / PermissionsScreen
```

### IMAP dual-client design

`com.sun.mail:android-mail` strips `IMAPSaslAuthenticator` from its Android build, so XOAUTH2 via SASL always throws `ClassNotFoundException` at runtime. **`ImapOAuthClient` bypasses this by speaking raw IMAP XOAUTH2 over an `SSLSocket`**; `ImapClient` is used only for PASSWORD accounts. Both return the same `FetchResult` type so `EmailPollWorker` is agnostic.

`ImapClient.fetchNew()` delegates internally to `ImapOAuthClient` when `account.authType != "PASSWORD"`.

### UID-based message tracking

New messages are found by IMAP UID (`lastSeenUid` + `uidValidity` persisted per account in Room), not by `\Seen` flag — this avoids marking messages as read. On first run, the current max UID is stored as baseline without processing any messages. If `uidValidity` changes, the baseline resets automatically.

### WorkManager jobs

| Work name | Type | Policy |
|-----------|------|--------|
| `email_poll` | `PeriodicWorkRequest` | `UPDATE` |
| `email_poll_now` | `OneTimeWorkRequest` | `REPLACE` |

`AccountsViewModel` observes `getWorkInfosForUniqueWorkFlow(SCAN_NOW_WORK)` and reads `KEY_NEW_COUNT` from `outputData` to display live scan state. The observation is cancelled in `viewModelScope` on terminal states (SUCCEEDED/FAILED/CANCELLED).

### OAuth flow

AppAuth-Android handles PKCE + Custom Tabs. Tokens are stored as JSON (`OAuthTokens`) in `EncryptedSharedPreferences` under key `oauth_<email>`. `OAuthTokenManager.getValidToken()` checks `accessTokenExpiry > now + 60s` before returning; expired tokens are refreshed transparently.

Google uses its own redirect URI scheme (`com.googleusercontent.apps.{CLIENT_ID}:/oauth2redirect`, opaque URI — intent-filter registers only the scheme). Microsoft/custom uses `com.jobalert://oauth2redirect` (hierarchical URI — registers scheme + host).

### Room migrations

All migrations are additive `ALTER TABLE` — never drop/recreate tables with data.

| Version | Change |
|---------|--------|
| 1→2 | `rules.isEnabled` |
| 2→3 | `email_accounts` table |
| 3→4 | `email_accounts.authType`, `email_accounts.oauthConfig` |
| 4→5 | `email_accounts.needsReauth` |

## OAuth client IDs

- `GOOGLE_CLIENT_ID` is hardcoded in `OAuthTokenManager.kt` (`OAuthClients` object).
- `MICROSOFT_CLIENT_ID` is `TODO_AZURE_CLIENT_ID` — requires an Azure App Registration.
- For Google: create an **Android** OAuth client in Google Cloud Console with the package name and the SHA-1 of the debug keystore. Enable `https://mail.google.com/` scope and IMAP in Gmail settings.

## Test layout

- `app/src/test/` — JVM unit tests: `RuleEngineTest`, `ImapClientSessionTest`, `OAuthTokenManagerTest`, `OAuthTokensSerializationTest`
- `app/src/androidTest/` — instrumented tests against a real Room DB: `AlertDaoTest`, `Migration3To4Test`, `SettingsRepositoryTest`
