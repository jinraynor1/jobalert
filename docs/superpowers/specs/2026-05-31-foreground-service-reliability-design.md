# Design: Foreground Service — Confiabilidad del monitor de alertas

**Fecha:** 2026-05-31
**Estado:** Aprobado

## Contexto

`AlertNotificationListenerService` corre en segundo plano. Android puede matar el proceso
por optimización de batería (especialmente en Xiaomi, Samsung, Huawei). El sistema reinicia
el servicio automáticamente en condiciones normales, pero fabricantes agresivos con la batería
pueden bloquearlo. El usuario quiere garantía de que el monitor esté siempre activo.

## Solución

Convertir `AlertNotificationListenerService` en foreground service llamando `startForeground()`
sobre sí mismo (soportado desde Android 9). Esto obliga al sistema operativo a mantener el
proceso vivo. Como efecto secundario, la notificación persistente actúa también como indicador
de alertas sin atender.

## Componentes afectados

### 1. AndroidManifest.xml

Permisos nuevos:
- `android.permission.FOREGROUND_SERVICE` — obligatorio para llamar `startForeground()`
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` — requerido en Android 14+ (API 34+)
- `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — para abrir el diálogo del sistema

Atributo en el servicio existente:
```xml
android:foregroundServiceType="specialUse"
```

### 2. JobAlertApp

Crear el `NotificationChannel` en `onCreate()` antes de que el servicio arranque:
- Channel ID: `"jobalert_service"`
- Nombre visible: `"JobAlert activo"`
- Importancia: `IMPORTANCE_LOW` (sin sonido, sin heads-up, sin vibración)

### 3. AlertDao

Nueva consulta:
```kotlin
@Query("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0")
fun getUnacknowledgedCount(): Flow<Int>
```

### 4. AlertRepository

Nueva propiedad:
```kotlin
val unacknowledgedCount: Flow<Int> = dao.getUnacknowledgedCount()
```

### 5. AlertNotificationListenerService

Cambios en `onListenerConnected()`:
1. Llamar `startForeground()` con condicional de API:
   - API < 34: `startForeground(NOTIFICATION_ID, buildNotification(0))`
   - API 34+: `startForeground(NOTIFICATION_ID, buildNotification(0), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`
   (`FOREGROUND_SERVICE_TYPE_SPECIAL_USE` fue añadido en API 34)
2. Lanzar coroutine en `serviceScope` que observa `app.alertRepository.unacknowledgedCount`
   y llama `updateNotification(count)` en cada emisión

Nuevas funciones privadas:
- `buildNotification(count: Int): Notification` — construye la notificación con el texto correcto
- `updateNotification(count: Int)` — llama `notificationManager.notify()` para actualizar en vivo

Texto de la notificación:
- `count == 0` → `"Escuchando notificaciones de Gmail"`
- `count > 0`  → `"$count alerta(s) sin atender"`

Tocar la notificación abre `MainActivity` vía `PendingIntent`.

`onDestroy()` ya cancela `serviceScope`, por lo que la coroutine de observación se cancela sola.

### 6. PermissionsScreen

Tercer `PermissionRow` con:
- Label: `"Sin restricción de batería"`
- Descripción: `"Evita que Android detenga el servicio de escucha"`
- Check: `PowerManager.isIgnoringBatteryOptimizations(context.packageName)`
- Botón: abre `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` con el package URI

### 7. res/drawable/ic_notification.xml

Vector drawable monocromático (24×24 dp) para el small icon de la notificación.
Usar un ícono de campana o escudo simple compatible con la barra de estado.

## Flujo de estado de la notificación

```
Boot / primer arranque
  └─► onListenerConnected()
        ├─► startForeground("Escuchando Gmail")
        └─► collect(unacknowledgedCount)
                ├─ count=0  →  "Escuchando notificaciones de Gmail"
                └─ count>0  →  "N alerta(s) sin atender"

Usuario toca "ATENDIDO" en overlay
  └─► acknowledged=1 en Room  →  Flow emite count-1  →  notificación se actualiza
```

## Lo que NO cambia

- `RuleEngine` — sin modificaciones
- `OverlayManager` — sin modificaciones
- Room schema — sin migraciones (solo se agrega una query)
- Pantallas de Reglas, Historial, Ajustes — sin modificaciones

## Verificación

1. Instalar APK, conceder los tres permisos desde la pantalla de Estado
2. Verificar que aparece la notificación persistente "Escuchando notificaciones de Gmail"
3. Forzar cierre de la app desde Ajustes del sistema → el servicio debe reaparecer al reiniciarse
4. Recibir un correo que coincida con una regla → notificación debe cambiar a "1 alerta sin atender"
5. Tocar "ATENDIDO" → notificación vuelve a "Escuchando notificaciones de Gmail"
6. Activar optimización de batería agresiva (o usar ADB: `adb shell dumpsys deviceidle force-idle`) → el servicio debe sobrevivir

## Archivos a crear / modificar

| Archivo | Acción |
|---|---|
| `app/src/main/AndroidManifest.xml` | Modificar |
| `app/src/main/java/com/jobalert/JobAlertApp.kt` | Modificar |
| `app/src/main/java/com/jobalert/data/db/AlertDao.kt` | Modificar |
| `app/src/main/java/com/jobalert/data/repository/AlertRepository.kt` | Modificar |
| `app/src/main/java/com/jobalert/service/AlertNotificationListenerService.kt` | Modificar |
| `app/src/main/java/com/jobalert/ui/permissions/PermissionsScreen.kt` | Modificar |
| `app/src/main/res/drawable/ic_notification.xml` | Crear |
