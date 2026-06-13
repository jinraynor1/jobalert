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
./gradlew :app:testDebugUnitTest --tests "com.jobalert.domain.rule.RuleEngineTest"

# Instrumented tests (requires connected device/emulator)
./gradlew :app:connectedDebugAndroidTest

# Run a single instrumented test class
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jobalert.data.AlertDaoTest

# Logcat — full polling cycle
adb logcat -s JobAlert-Worker,JobAlert-OAuth,JobAlert-ImapOAuth,JobAlert-IMAP
```

## Architecture

Clean Architecture by layers. Dependency rule: `ui → domain ← data`. `domain` has zero dependencies on Android, Room, or data sources.

All repositories and services are instantiated as `lazy` properties on `JobAlertApp` (manual DI — no Hilt/Koin), declared with their `domain` interface types. ViewModels access them via `(application as JobAlertApp).<property>`.

```
JobAlertApp (Application) — composition root
  ├── AppDatabase (Room v8)       ← data.local.db
  ├── AlertRepository             ← interface in domain, impl in data.repository
  ├── RuleRepository              ← interface in domain, impl in data.repository
  ├── EmailAccountRepository      ← interface in domain, impl in data.repository
  ├── SettingsRepository          ← interface in domain, impl in data.repository
  ├── CredentialStore             ← data.local.credential
  ├── MailAuthGateway             ← interface in domain, impl in data.remote
  └── OverlayManager              ← ui.overlay

WorkManager
  └── EmailPollWorker (CoroutineWorker)   ← background/
        ├── ImapClient            ← data.remote.imap, PASSWORD accounts only
        ├── ImapOAuthClient       ← data.remote.imap, raw SSLSocket, OAuth accounts
        ├── OAuthTokenManager     ← data.remote.oauth, AppAuth-Android token refresh
        └── RuleEngine            ← domain.rule, stateless pure matching

UI (Jetpack Compose, Navigation Compose)
  MainActivity → AppNavigation
    AccountsScreen / RulesScreen / HistoryScreen / SettingsScreen / PermissionsScreen
  OverlayManager                  ← ui.overlay, TYPE_APPLICATION_OVERLAY via ComposeView
```

### Layer structure

```
domain/
  model/        Rule · Alert · EmailAccount · NotificationData · OAuthConfig
  repository/   RuleRepository · AlertRepository · EmailAccountRepository
                SettingsRepository · MailAuthGateway   (interfaces only)
  rule/         RuleEngine

data/
  local/
    entity/     RuleEntity · AlertEntity · EmailAccountEntity  (@Entity)
    db/         AppDatabase · AlertDao · RuleDao · EmailAccountDao · Converters
    credential/ CredentialStore · OAuthTokens
  remote/
    imap/       ImapClient · ImapOAuthClient · FetchResult
    oauth/      OAuthTokenManager · OAuthClients
    MailAuthGatewayImpl
  mapper/       Mappers.kt  (toDomain() / toEntity())
  repository/   AlertRepositoryImpl · RuleRepositoryImpl
                EmailAccountRepositoryImpl · SettingsRepositoryImpl

ui/
  accounts/ · history/ · rules/ · settings/ · permissions/ · theme/
  overlay/    OverlayManager · ServiceLifecycleOwner

background/
  EmailPollWorker
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

The `MailAuthGateway` interface (`domain.repository`) abstracts IMAP connection testing and OAuth intent building so ViewModels never import from `data.remote` directly. `MailAuthGatewayImpl` (`data.remote`) delegates to `ImapClient` and `OAuthTokenManager`.

Google uses its own redirect URI scheme (`com.googleusercontent.apps.{CLIENT_ID}:/oauth2redirect`, opaque URI — intent-filter registers only the scheme). Microsoft/custom uses `com.jobalert://oauth2redirect` (hierarchical URI — registers scheme + host).

### Room migrations

All migrations are additive `ALTER TABLE` — never drop/recreate tables with data.

| Version | Change |
|---------|--------|
| 1→2 | `rules.isEnabled` |
| 2→3 | `email_accounts` table |
| 3→4 | `email_accounts.authType`, `email_accounts.oauthConfig` |
| 4→5 | `email_accounts.needsReauth` |
| 5→6 | `rules.alertColor` |
| 6→7 | Recreación de tabla `rules` (agrega campo `name`) |
| 7→8 | `alerts.ruleName`, `rules.position` |

The Room entity classes are `RuleEntity`, `AlertEntity`, `EmailAccountEntity` in `data.local.entity`. The domain model classes (`Rule`, `Alert`, `EmailAccount`) in `domain.model` have no Room annotations. Mappers in `data.mapper.Mappers` convert between the two.

## OAuth client IDs

- `GOOGLE_CLIENT_ID` is hardcoded in `OAuthTokenManager.kt` (`OAuthClients` object) at `data/remote/oauth/`.
- `MICROSOFT_CLIENT_ID` is `TODO_AZURE_CLIENT_ID` — requires an Azure App Registration.
- For Google: create an **Android** OAuth client in Google Cloud Console with the package name and the SHA-1 of the debug keystore. Enable `https://mail.google.com/` scope and IMAP in Gmail settings.

## Test layout

- `app/src/test/` — JVM unit tests (no device required):
  - `domain/rule/RuleEngineTest`
  - `data/remote/imap/ImapClientSessionTest`
  - `data/remote/oauth/OAuthTokenManagerTest`
  - `data/remote/oauth/OAuthTokensSerializationTest`
- `app/src/androidTest/` — instrumented tests against a real Room DB:
  - `data/AlertDaoTest`
  - `data/Migration3To4Test`, `Migration4To5Test`, `Migration7To8Test`
  - `data/SettingsRepositoryTest`
