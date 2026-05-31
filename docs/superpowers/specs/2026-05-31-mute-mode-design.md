# Design: Modo Silencio desde la notificación persistente

**Fecha:** 2026-05-31
**Estado:** Aprobado

## Contexto

Durante una avería en curso, el sistema puede generar múltiples alertas concurrentes. El usuario ya sabe que hay un problema y no quiere ser interrumpido por el overlay repetidamente, pero sí necesita que las alertas queden registradas en el historial para revisarlas después.

## Solución

Agregar botones de acción a la notificación persistente del foreground service: **"1h / 2h / 8h"** para silenciar, y **"Reactivar"** cuando ya está silenciado. Un `BroadcastReceiver` maneja las acciones y programa un `AlarmManager` para la expiración automática. El servicio respeta el silencio omitiendo el overlay pero siempre guarda en historial.

## Flujo de datos

```
Usuario toca "1h" en la notificación
  → PendingIntent → SilenceReceiver(ACTION_SILENCE, duration=1h)
    → SettingsRepository.muteUntil = now + 1h
    → AlarmManager.setAndAllowWhileIdle(now + 1h) → SilenceReceiver(ACTION_MUTE_EXPIRED)
    → notificationManager.notify() → "Silenciado hasta HH:mm" + botón "Reactivar"

Gmail llega con regla coincidente (mientras silenciado)
  → onNotificationPosted() → RuleEngine.match() = true
    → alertRepository.insert() ← guardado en historial ✓
    → settingsRepository.isMuted = true → overlayManager.show() NO se llama ✓

AlarmManager dispara al expirar
  → SilenceReceiver(ACTION_MUTE_EXPIRED)
    → SettingsRepository.muteUntil = 0
    → notificationManager.notify() → "Escuchando Gmail" + botones "1h" "2h" "8h"

Usuario toca "Reactivar" antes de expirar
  → SilenceReceiver(ACTION_UNSILENCE)
    → SettingsRepository.muteUntil = 0
    → AlarmManager cancela alarma pendiente
    → notificationManager.notify() → estado normal
```

## Componentes afectados

### 1. SettingsRepository

Nuevas propiedades:

```kotlin
var muteUntil: Long          // SharedPreferences key "mute_until", default 0L
    get() = prefs.getLong("mute_until", 0L)
    set(value) { prefs.edit().putLong("mute_until", value).apply() }

val isMuted: Boolean
    get() = muteUntil > System.currentTimeMillis()
```

### 2. NotificationHelper.kt (nuevo)

Archivo: `app/src/main/java/com/jobalert/service/NotificationHelper.kt`

Función top-level:

```kotlin
fun buildServiceNotification(
    context: Context,
    unacknowledgedCount: Int,
    isMuted: Boolean,
    muteUntil: Long
): Notification
```

Comportamiento:
- **No silenciado:** texto "Escuchando notificaciones de Gmail" (o "N alerta(s) sin atender"), 3 botones de acción: "1h", "2h", "8h"
- **Silenciado:** texto "Silenciado hasta HH:mm", 1 botón de acción: "Reactivar"
- El texto de tiempo usa `SimpleDateFormat("HH:mm")` sobre `muteUntil`
- Los botones disparan `PendingIntent.getBroadcast()` hacia `SilenceReceiver` con las acciones correspondientes
- Request codes distintos por botón para evitar colisiones de PendingIntent

Constantes de request code (dentro del archivo):
```
RC_SILENCE_1H = 10
RC_SILENCE_2H = 11
RC_SILENCE_8H = 12
RC_UNSILENCE  = 13
RC_EXPIRED    = 14
```

### 3. SilenceReceiver.kt (nuevo)

Archivo: `app/src/main/java/com/jobalert/service/SilenceReceiver.kt`

```kotlin
class SilenceReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SILENCE = "com.jobalert.SILENCE"
        const val ACTION_UNSILENCE = "com.jobalert.UNSILENCE"
        const val ACTION_MUTE_EXPIRED = "com.jobalert.MUTE_EXPIRED"
        const val EXTRA_DURATION_MS = "duration_ms"
    }

    override fun onReceive(context: Context, intent: Intent) { ... }
}
```

Lógica de `onReceive`:
- `ACTION_SILENCE`: lee `EXTRA_DURATION_MS`, calcula `muteUntil = now + duration`, guarda en `SettingsRepository`, llama `scheduleExpiry(context, muteUntil)`, actualiza notificación (cuando está silenciado no se muestra el conteo, solo "Silenciado hasta HH:mm")
- `ACTION_UNSILENCE`: limpia `muteUntil = 0`, cancela alarma con `AlarmManager.cancel()`, lee conteo actual con `runBlocking { app.alertRepository.unacknowledgedCount.first() }`, actualiza notificación
- `ACTION_MUTE_EXPIRED`: igual que `ACTION_UNSILENCE` pero sin `AlarmManager.cancel()` (ya expiró sola)

El uso de `runBlocking` en `onReceive()` es aceptable: `BroadcastReceiver.onReceive()` corre en el hilo principal y debe completarse rápido, pero leer un `Int` de Room es una operación de microsegundos.

Función `scheduleExpiry`:
```kotlin
private fun scheduleExpiry(context: Context, muteUntil: Long) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, RC_EXPIRED,
        Intent(context, SilenceReceiver::class.java).apply { action = ACTION_MUTE_EXPIRED },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, muteUntil, pendingIntent)
}
```

`setAndAllowWhileIdle()` no requiere `SCHEDULE_EXACT_ALARM`. Para duraciones de 1h/2h/8h, el desfase máximo de ~10 min en Doze es aceptable.

### 4. AlertNotificationListenerService

Cambios:
- `buildServiceNotification()` se elimina del servicio y se delega a `NotificationHelper.buildServiceNotification()`
- En `onListenerConnected()` y en el `collect`, pasar `settingsRepository.isMuted` y `settingsRepository.muteUntil` a `buildServiceNotification()`
- En `onNotificationPosted()`, después de `RuleEngine.match() = true`:

```kotlin
// ANTES (actual):
app.alertRepository.insert(...)
app.overlayManager.show(data)

// DESPUÉS:
app.alertRepository.insert(...)
if (!app.settingsRepository.isMuted) {
    app.overlayManager.show(data)
}
```

### 5. AndroidManifest.xml

Registrar `SilenceReceiver` como no exportado:

```xml
<receiver
    android:name=".service.SilenceReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.jobalert.SILENCE" />
        <action android:name="com.jobalert.UNSILENCE" />
        <action android:name="com.jobalert.MUTE_EXPIRED" />
    </intent-filter>
</receiver>
```

## Lo que NO cambia

- `SettingsScreen` — sin cambios de UI (la activación es desde la notificación)
- `RuleEngine` — sin cambios
- `OverlayManager` — sin cambios
- Room schema — sin migraciones

## Verificación

1. **Sin silencio:** la notificación muestra "Escuchando notificaciones de Gmail" con botones "1h", "2h", "8h"
2. **Activar silencio:** tocar "1h" → notificación cambia a "Silenciado hasta HH:mm" con botón "Reactivar"
3. **Alerta durante silencio:** recibir correo que coincida → aparece en historial, overlay NO aparece
4. **Reactivar manual:** tocar "Reactivar" → notificación vuelve al estado normal inmediatamente
5. **Expiración automática:** esperar a que expire el tiempo → notificación vuelve sola al estado normal
6. **Re-silenciar:** si ya está silenciado y se toca otro botón de duración (ej. "2h"), sobreescribe el silencio anterior

## Archivos a crear / modificar

| Archivo | Acción |
|---|---|
| `app/src/main/java/com/jobalert/data/repository/SettingsRepository.kt` | Modificar |
| `app/src/main/java/com/jobalert/service/NotificationHelper.kt` | Crear |
| `app/src/main/java/com/jobalert/service/SilenceReceiver.kt` | Crear |
| `app/src/main/java/com/jobalert/service/AlertNotificationListenerService.kt` | Modificar |
| `app/src/main/AndroidManifest.xml` | Modificar |
