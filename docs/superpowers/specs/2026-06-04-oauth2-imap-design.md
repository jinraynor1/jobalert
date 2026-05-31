# OAuth 2.0 como alternativa de autenticación IMAP

**Fecha:** 2026-06-04  
**Estado:** Aprobado

## Contexto

JobAlert accede al correo de los usuarios vía IMAP. Actualmente solo soporta usuario +
contraseña. El objetivo es añadir OAuth 2.0 (XOAUTH2 sobre IMAP) como alternativa para
Gmail, Microsoft 365 y proveedores corporativos con servidor OAuth propio. La autenticación
por contraseña se mantiene sin cambios para proveedores que no soporten OAuth.

## Librería

`net.openid:appauth:0.11.1` — AppAuth for Android (OpenID Foundation). Gestiona el flujo
de browser (Custom Tabs + PKCE), el intercambio código → tokens, y el refresh. Una sola
librería para los tres tipos de proveedor.

## Arquitectura y flujo de datos

```
EmailPollWorker
  → when (account.authType)
      "PASSWORD"  → CredentialStore.getPassword(email)
      "OAUTH2_*"  → OAuthTokenManager.getValidToken(account, credentialStore, context)
                        token vigente (expiry > now + 60s) → devuelve access token
                        token expirado → AuthorizationService.performTokenRequest(refresh)
                                          ok  → guarda nuevos tokens → devuelve access token
                                          err → devuelve null → skip cuenta + notificación
  → credential == null → continuar con siguiente cuenta
  → ImapClient.fetchNew(account, credential)
      PASSWORD   → IMAP AUTH PLAIN / LOGIN (comportamiento actual)
      OAUTH2_*   → IMAP SASL XOAUTH2 (mismo host/puerto; access token como "contraseña")
```

El refresh de tokens es transparente. Solo si el refresh token está revocado se notifica
al usuario.

## Modelo de datos

### EmailAccount (Room)

Dos columnas nuevas — migración 3 → 4:

```sql
ALTER TABLE email_accounts ADD COLUMN authType TEXT NOT NULL DEFAULT 'PASSWORD'
ALTER TABLE email_accounts ADD COLUMN oauthConfig TEXT
```

Valores de `authType`: `PASSWORD` | `OAUTH2_GOOGLE` | `OAUTH2_MICROSOFT` | `OAUTH2_CUSTOM`

`oauthConfig` es nullable. Solo se rellena para `OAUTH2_CUSTOM`. Almacena JSON con:
`authEndpoint`, `tokenEndpoint`, `clientId`, `scope`.

Las cuentas existentes migran con `authType = 'PASSWORD'` y `oauthConfig = NULL`.

### CredentialStore (EncryptedSharedPreferences)

API extendida sin romper la existente:

```kotlin
// Sin cambios
fun setPassword(email, password)
fun getPassword(email): String?
fun removePassword(email)          // también limpia tokens OAuth si los hubiera

// Nuevo — clave interna "oauth_${email}"
fun setOAuthTokens(email, OAuthTokens)
fun getOAuthTokens(email): OAuthTokens?
```

```kotlin
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiry: Long   // epoch ms; -1 = desconocido (fuerza refresh en próxima ejecución)
)
```

Serializado a JSON con Gson (ya en el proyecto). Contraseña y tokens nunca conviven en la
misma cuenta.

## Componentes nuevos

### OAuthTokenManager (object)

**`getValidToken(account, credentialStore, context): String?`**
- Lee `OAuthTokens` de `CredentialStore`.
- Si `accessTokenExpiry > now + 60_000` ms → devuelve `accessToken`.
- Si no → llama a `AuthorizationService.performTokenRequest` con el `refreshToken`.
  - Éxito: actualiza `OAuthTokens` en `CredentialStore` → devuelve nuevo `accessToken`.
  - Fallo: devuelve `null` (el worker omite la cuenta y emite una notificación).

**`buildAuthorizationIntent(authType, oauthConfig?, context): Intent`**
- Construye `AuthorizationRequest` + obtiene el Intent de Custom Tabs para el proveedor.
- `OAUTH2_GOOGLE` → discovery `https://accounts.google.com/.well-known/openid-configuration`
- `OAUTH2_MICROSOFT` → discovery `https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration`
- `OAUTH2_CUSTOM` → endpoints manuales del `oauthConfig`

**`OAuthClients` (object, mismo archivo)**
```kotlin
object OAuthClients {
    const val GOOGLE_CLIENT_ID    = "TODO_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
    const val MICROSOFT_CLIENT_ID = "TODO_AZURE_CLIENT_ID"
}
```
El desarrollador rellena estos antes de compilar. Para `OAUTH2_CUSTOM`, el `clientId` viene
del `oauthConfig`.

**Scopes:**
- Google: `"https://mail.google.com/"`
- Microsoft: `"https://outlook.office.com/IMAP.AccessAsUser.All offline_access"`
- Custom: el configurado por el usuario

### OAuthConfig (data class)

```kotlin
data class OAuthConfig(
    val authEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val scope: String
)
```

Serializado a JSON para el campo `oauthConfig` de `EmailAccount`.

## ImapClient — cambios

`buildSession()` recibe `useXoauth2: Boolean`. Si es true añade:
```
mail.imaps.sasl.enable = true
mail.imaps.sasl.mechanisms = XOAUTH2
mail.imap.sasl.enable = true
mail.imap.sasl.mechanisms = XOAUTH2
```

`fetchNew` y `testConnection` derivan el flag de `account.authType != "PASSWORD"`.
`store.connect(host, port, email, credential)` es idéntico — el access token se pasa como
argumento `password`; Jakarta Mail gestiona el encoding XOAUTH2.

Hosts y puertos por defecto para cuentas OAuth (si no se especifican manualmente):
- Google: `imap.gmail.com:993`
- Microsoft: `outlook.office365.com:993`

## EmailPollWorker — cambios

```kotlin
val credential: String? = when (account.authType) {
    "PASSWORD" -> app.credentialStore.getPassword(account.email)
    else       -> OAuthTokenManager.getValidToken(account, app.credentialStore, applicationContext)
}
if (credential == null) {
    // emitir notificación "Re-autoriza tu cuenta ${account.email}"
    continue
}
```

## Manifest

```xml
<!-- Redirect URI handler de AppAuth -->
<activity android:name="net.openid.appauth.RedirectUriReceiverActivity"
          android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="com.jobalert" android:host="oauth2redirect" />
    </intent-filter>
</activity>
```

## UI — AccountDialog reestructurado

El diálogo muestra tres secciones expandibles (solo una puede estar abierta a la vez):

```
┌──────────────────────────────────────┐
│  Nueva cuenta                        │
│                                      │
│  [G] Conectar con Gmail              │
│  [M] Conectar con Microsoft          │
│  ────────────── o ─────────────────  │
│  ▶ OAuth personalizado               │  expandible:
│      Auth URL / Token URL            │  authEndpoint, tokenEndpoint,
│      Client ID / Scope               │  clientId, scope
│      Host / Puerto / SSL             │  (host IMAP del proveedor)
│      [Autorizar →]                   │
│  ────────────────────────────────────│
│  ▶ Contraseña IMAP                   │  expandible: formulario actual
│      Email / Contraseña              │  + [Probar conexión]
│      Host / Puerto / SSL             │
└──────────────────────────────────────┘
```

Para `OAUTH2_GOOGLE` y `OAUTH2_MICROSOFT`, el host y puerto IMAP son preset (hardcodeados)
y no se muestran en la UI. Para `OAUTH2_CUSTOM`, el usuario los ingresa en el formulario.

**Flujo OAuth desde Compose:**

`rememberLauncherForActivityResult(StartActivityForResult())` declarado en `AccountsScreen`
(no dentro del diálogo — restricción de Compose). La lambda recibe el `Intent` de retorno y
llama a `viewModel.handleOAuthResult(data, pendingAuthType)`.

`AccountsViewModel.handleOAuthResult()`:
1. Extrae `AuthorizationResponse` del Intent.
2. Llama suspend a `AuthorizationService.performTokenRequest` (intercambio código → tokens).
3. Guarda `OAuthTokens` en `CredentialStore`.
4. Inserta `EmailAccount` en Room con `authType` correspondiente.
5. El diálogo se cierra cuando `accounts` StateFlow emite la nueva cuenta.

**Editar cuenta OAuth** — muestra email (read-only) + botón "Re-autorizar" que relanza el
flujo. No hay campos de contraseña visibles.

**AccountCard** — subtítulo según `authType`:
- `PASSWORD` → `${account.host}:${account.port} (SSL)` (comportamiento actual)
- `OAUTH2_GOOGLE` → `Google OAuth 2.0`
- `OAUTH2_MICROSOFT` → `Microsoft OAuth 2.0`
- `OAUTH2_CUSTOM` → `OAuth personalizado · ${account.host}:${account.port}`

## Archivos afectados

| Acción   | Archivo |
|----------|---------|
| Modificar | `gradle/libs.versions.toml` |
| Modificar | `app/build.gradle.kts` |
| Modificar | `AndroidManifest.xml` |
| Modificar | `data/model/EmailAccount.kt` |
| Modificar | `data/db/AppDatabase.kt` (versión 4, MIGRATION_3_4) |
| Modificar | `data/repository/CredentialStore.kt` |
| Modificar | `imap/ImapClient.kt` |
| Modificar | `work/EmailPollWorker.kt` |
| Modificar | `ui/accounts/AccountsScreen.kt` |
| Modificar | `ui/accounts/AccountsViewModel.kt` |
| Crear     | `oauth/OAuthTokenManager.kt` (incluye `OAuthClients`) |
| Crear     | `oauth/OAuthConfig.kt` |

## Fuera de alcance

- Soporte de `account_type` en el Android AccountManager (integración con cuentas del SO).
- Revocación de tokens desde la app (el usuario lo hace en la consola del proveedor).
- Prueba de conexión OAuth desde el diálogo de edición (el token ya existe; se considera válido).
- Múltiples scopes configurables por el usuario.
