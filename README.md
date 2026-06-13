# JobAlert

[![CI](https://github.com/jinraynor1/jobalert/actions/workflows/ci.yml/badge.svg)](https://github.com/jinraynor1/jobalert/actions/workflows/ci.yml)
[![Tests instrumentados](https://github.com/jinraynor1/jobalert/actions/workflows/instrumented-tests.yml/badge.svg)](https://github.com/jinraynor1/jobalert/actions/workflows/instrumented-tests.yml)

Aplicación Android que monitorea cuentas de correo IMAP en busca de mensajes que coincidan con reglas configuradas por el usuario, mostrando una alerta visual de pantalla completa cuando hay coincidencia.

## Funcionalidad

- Conecta una o varias cuentas de correo (Gmail, Microsoft 365, IMAP genérico)
- Autenticación por contraseña o OAuth 2.0 (Google, Microsoft, proveedor personalizado)
- Reglas configurables: filtra por remitente y/o palabras clave en asunto y cuerpo
- Overlay de pantalla completa con sonido y vibración al detectar un correo relevante
- Revisión periódica vía WorkManager (mínimo 15 min, configurable)
- Botón "Escanear ahora" para revisión inmediata con estado en vivo
- Historial de alertas disparadas
- Horario silencioso, intervalo mínimo entre alertas y modo mute

---

## Arquitectura

Clean Architecture por capas. La regla de dependencias es `ui → domain ← data`; `domain` no depende de nadie.

```
┌─────────────────────────────────────────────────────────────┐
│                    ui (Jetpack Compose)                      │
│  MainActivity → AppNavigation                                │
│  ├── accounts/  AccountsScreen  + AccountsViewModel         │
│  ├── rules/     RulesScreen     + RulesViewModel            │
│  ├── history/   HistoryScreen   + HistoryViewModel          │
│  ├── settings/  SettingsScreen  + SettingsViewModel         │
│  ├── permissions/ PermissionsScreen                         │
│  └── overlay/   OverlayManager + ServiceLifecycleOwner      │
└─────────────────────┬───────────────────────────────────────┘
                      │  interfaces de domain
┌─────────────────────▼───────────────────────────────────────┐
│                      domain                                  │
│  model/   Rule · Alert · EmailAccount · NotificationData    │
│           OAuthConfig                                        │
│  repository/  RuleRepository · AlertRepository              │
│               EmailAccountRepository · SettingsRepository   │
│               MailAuthGateway                               │
│  rule/    RuleEngine  (matching puro, sin dependencias)     │
└─────────────────────┬───────────────────────────────────────┘
                      │  implementa interfaces de domain
┌─────────────────────▼───────────────────────────────────────┐
│                       data                                   │
│  local/                                                      │
│  ├── entity/  RuleEntity · AlertEntity · EmailAccountEntity │
│  ├── db/      AppDatabase · AlertDao · RuleDao              │
│  │            EmailAccountDao · Converters                  │
│  └── credential/  CredentialStore · OAuthTokens            │
│  remote/                                                     │
│  ├── imap/   ImapClient (password) · ImapOAuthClient (OAuth)│
│  ├── oauth/  OAuthTokenManager · OAuthClients               │
│  └── MailAuthGatewayImpl                                    │
│  mapper/   Mappers.kt  (entity ↔ domain)                   │
│  repository/  *RepositoryImpl  (implementan interfaces)     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   background (WorkManager)                   │
│  EmailPollWorker                                            │
└─────────────────────────────────────────────────────────────┘

JobAlertApp (Application) — composition root (DI manual)
```

### Paquetes

| Paquete | Responsabilidad |
|---------|----------------|
| `domain.model` | POJOs puros: `Rule`, `Alert`, `EmailAccount`, `NotificationData`, `OAuthConfig` |
| `domain.repository` | Interfaces: `RuleRepository`, `AlertRepository`, `EmailAccountRepository`, `SettingsRepository`, `MailAuthGateway` |
| `domain.rule` | `RuleEngine` — evaluación de reglas (sin dependencias externas) |
| `data.local.entity` | Entidades Room: `RuleEntity`, `AlertEntity`, `EmailAccountEntity` |
| `data.local.db` | `AppDatabase`, DAOs, `Converters` |
| `data.local.credential` | `CredentialStore` (EncryptedSharedPreferences), `OAuthTokens` |
| `data.remote.imap` | `ImapClient` (password), `ImapOAuthClient` (XOAUTH2), `FetchResult` |
| `data.remote.oauth` | `OAuthTokenManager`, `OAuthClients` |
| `data.remote` | `MailAuthGatewayImpl` |
| `data.mapper` | Funciones `toDomain()` / `toEntity()` |
| `data.repository` | `*RepositoryImpl` — implementan las interfaces de `domain` |
| `ui` | Pantallas Compose + ViewModels + `overlay/` |
| `background` | `EmailPollWorker` |

---

## Flujo de revisión de correo

```
WorkManager (periódico o one-shot)
  └── EmailPollWorker.doWork()
        ├── Obtiene cuentas habilitadas y reglas via interfaces de domain
        ├── Para cada cuenta:
        │     ├── [PASSWORD] → CredentialStore.getPassword()
        │     │                → ImapClient.fetchNew()  (android-mail)
        │     └── [OAUTH2]   → OAuthTokenManager.getValidToken()
        │                      → ImapOAuthClient.fetchNew()  (SSL socket directo)
        ├── RuleEngine.match(mensaje, reglas)
        ├── Si coincide → AlertRepository.insert(Alert) + OverlayManager.show()
        └── Actualiza lastSeenUid y uidValidity en Room
```

---

## Autenticación

### Password (IMAP estándar)

Las contraseñas se almacenan en `EncryptedSharedPreferences` con AES-256-GCM. Se pasan directamente a `javax.mail` (android-mail) en el método `store.connect()`.

### OAuth 2.0

El flujo OAuth usa **AppAuth-Android** (`net.openid:appauth:0.11.1`) para el intercambio de código PKCE en Custom Tabs. Los tokens (access token + refresh token + expiración) se serializan como JSON y se guardan en `EncryptedSharedPreferences` con la clave `oauth_<email>`.

```
Usuario toca "Conectar con Gmail"
  → MailAuthGateway.buildAuthorizationIntent()
      → OAuthTokenManager fetches discovery document (accounts.google.com)
      → Abre Custom Tab con el authorization URL
  ← Redirect URI interceptado por RedirectUriReceiverActivity
  → AccountsViewModel.handleOAuthResult()
      → Intercambia code por tokens (AppAuth, en el ViewModel)
      → MailAuthGateway.storeTokensFromResponse()
      → EmailAccountRepository.insert()

En cada ciclo de polling:
  → OAuthTokenManager.getValidToken()
      → Si accessToken válido (expiry > now + 60s): lo devuelve directamente
      → Si expirado: hace refresh vía AppAuth y actualiza CredentialStore
```

#### Scopes configurados

| Proveedor | Scope |
|-----------|-------|
| Google | `https://mail.google.com/` |
| Microsoft | `https://outlook.office.com/IMAP.AccessAsUser.All offline_access` |

#### Redirect URIs

- **Google Android client**: `com.googleusercontent.apps.{CLIENT_ID}:/oauth2redirect`  
  URI opaca (esquema = client ID invertido, sin host) → el intent-filter en el manifest registra solo el scheme, sin `android:host`.
- **Microsoft / custom**: `com.jobalert://oauth2redirect`  
  URI jerárquica → el intent-filter registra `android:scheme="com.jobalert"` y `android:host="oauth2redirect"`.

---

## IMAP — Decisión técnica: ImapOAuthClient

La librería `com.sun.mail:android-mail` excluye `IMAPSaslAuthenticator` de su build para Android porque `javax.security.sasl` no estaba disponible cuando se construyó la librería. Esto hace que XOAUTH2 vía SASL falle siempre con `ClassNotFoundException` en tiempo de ejecución.

**Solución:** `ImapOAuthClient` implementa el protocolo IMAP XOAUTH2 directamente sobre un `SSLSocket`, sin pasar por el framework SASL:

1. Abre `SSLSocket` a `host:port`
2. Envía `AUTHENTICATE XOAUTH2 <base64(user=email\x01auth=Bearer token\x01\x01)>` como initial response
3. Si Gmail responde con `+ <challenge>` (detalle de error en base64), lo loguea y envía línea vacía para abortar
4. Si la autenticación es exitosa: `EXAMINE INBOX` → `UID SEARCH` → `UID FETCH RFC822`
5. Parsea el contenido RFC822 con `MimeMessage` de android-mail (solo para parsing, no para la conexión)
6. Devuelve un `FetchResult` idéntico al de `ImapClient`, transparente para el worker

`ImapClient` (android-mail) se sigue usando para cuentas con contraseña.

### Tracking de mensajes nuevos (UID-based)

Se usa el mecanismo IMAP UID en lugar de `\Seen` para evitar marcar mensajes como leídos:

- `lastSeenUid` y `uidValidity` se persisten por cuenta en Room
- En el primer run se registra el UID más alto actual como baseline (no se procesan mensajes existentes)
- Si `uidValidity` cambia (inbox recreado), se reinicia el baseline automáticamente

---

## Base de datos (Room v8)

| Versión | Cambio |
|---------|--------|
| 1→2 | `rules.isEnabled` |
| 2→3 | Tabla `email_accounts` |
| 3→4 | `email_accounts.authType`, `email_accounts.oauthConfig` |
| 4→5 | `email_accounts.needsReauth` |
| 5→6 | `rules.alertColor` |
| 6→7 | Recreación de tabla `rules` (agrega campo `name`) |
| 7→8 | `alerts.ruleName`, `rules.position` |

Las migraciones son aditivas (`ALTER TABLE`) — nunca se recrea una tabla con datos (excepción: 6→7 por incompatibilidad de esquema previo).

---

## WorkManager

| Trabajo | Tipo | Política | Trigger |
|---------|------|----------|---------|
| `email_poll` | `PeriodicWorkRequest` | `UPDATE` | Cada N minutos (mín. 15, configurable) |
| `email_poll_now` | `OneTimeWorkRequest` | `REPLACE` | Botón "Escanear ahora" |

El worker devuelve `KEY_NEW_COUNT` en `outputData` al terminar. El `AccountsViewModel` observa el WorkInfo via `getWorkInfosForUniqueWorkFlow` y muestra el resultado en la UI durante 2 segundos.

---

## Overlay

`OverlayManager` usa `TYPE_APPLICATION_OVERLAY` (requiere permiso `SYSTEM_ALERT_WINDOW`) para mostrar una pantalla de pantalla completa sobre cualquier app. Internamente usa un `ComposeView` con un `ServiceLifecycleOwner` propio para satisfacer el ciclo de vida requerido por Compose fuera de una `Activity`.

Si llega una segunda alerta mientras el overlay está visible, se encola y se muestra al presionar "Atendido".

---

## Stack técnico

| Componente | Librería |
|------------|----------|
| UI | Jetpack Compose + Material 3 |
| Navegación | Navigation Compose |
| ViewModel | Lifecycle ViewModel |
| Base de datos | Room 2.6 |
| Background | WorkManager 2.9 |
| IMAP (password) | `com.sun.mail:android-mail` 1.6.7 |
| IMAP (OAuth) | SSL socket directo (`ImapOAuthClient`) |
| OAuth 2.0 | AppAuth-Android 0.11.1 |
| Credenciales | EncryptedSharedPreferences (AES-256-GCM) |
| Serialización | Gson 2.10 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

---

## Permisos

| Permiso | Para qué |
|---------|----------|
| `SYSTEM_ALERT_WINDOW` | Overlay de pantalla completa |
| `POST_NOTIFICATIONS` | Notificación de re-autorización OAuth |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Mantener WorkManager activo |
| `VIBRATE` | Vibración en alerta |
| `INTERNET` | Conexión IMAP y OAuth |

---

## Logging (Logcat)

| Tag | Contenido |
|-----|-----------|
| `JobAlert-Worker` | Ciclo de polling: cuentas, mensajes fetched, reglas coincidentes |
| `JobAlert-OAuth` | Estado del token: válido/expirado/refrescado/error, tokeninfo scopes |
| `JobAlert-ImapOAuth` | Diálogo IMAP raw para cuentas OAuth (XOAUTH2, UIDs, parse) |
| `JobAlert-IMAP` | Errores de conexión IMAP para cuentas con contraseña |

```bash
# Ver todo el flujo de un escaneo
adb logcat -s JobAlert-Worker,JobAlert-OAuth,JobAlert-ImapOAuth,JobAlert-IMAP
```

---

## Configuración de desarrollo

### Google OAuth

1. Crear proyecto en [Google Cloud Console](https://console.cloud.google.com)
2. Activar la API Gmail
3. Crear credencial OAuth → Tipo: **Android** → ingresar package name y SHA-1 del keystore de debug
4. Habilitar scope `https://mail.google.com/` en la pantalla de consentimiento
5. Copiar el Client ID al campo `GOOGLE_CLIENT_ID` en `data/remote/oauth/OAuthTokenManager.kt`
6. En Gmail del usuario: **Configuración → Reenvío y POP/IMAP → Habilitar IMAP**

### Microsoft OAuth

Registrar la app en Azure Portal y reemplazar `TODO_AZURE_CLIENT_ID` en `data/remote/oauth/OAuthTokenManager.kt`.

### Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
