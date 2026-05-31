# Foreground Service — Confiabilidad Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convertir `AlertNotificationListenerService` en foreground service para que Android no lo mate por optimización de batería, y mostrar en la notificación persistente el conteo de alertas sin atender.

**Architecture:** `NotificationListenerService` llama `startForeground()` sobre sí mismo en `onListenerConnected()`. Una coroutine observa `AlertDao.getUnacknowledgedCount()` y actualiza el texto de la notificación en tiempo real. `JobAlertApp` crea el `NotificationChannel` en `onCreate()`. `PermissionsScreen` agrega un tercer card para que el usuario excluya la app de la optimización de batería.

**Tech Stack:** Android NotificationManager, NotificationCompat (androidx.core — disponible transitivamente), Room Flow, PendingIntent, PowerManager.

---

## Mapa de archivos

| Archivo | Acción |
|---|---|
| `app/src/main/res/drawable/ic_notification.xml` | Crear — ícono monocromático para la barra de estado |
| `app/src/main/AndroidManifest.xml` | Modificar — 3 permisos + `foregroundServiceType` en el servicio |
| `app/src/androidTest/java/com/jobalert/data/AlertDaoTest.kt` | Modificar — nuevo test para `getUnacknowledgedCount` |
| `app/src/main/java/com/jobalert/data/db/AlertDao.kt` | Modificar — nueva query |
| `app/src/main/java/com/jobalert/data/repository/AlertRepository.kt` | Modificar — nueva propiedad Flow |
| `app/src/main/java/com/jobalert/JobAlertApp.kt` | Modificar — canal de notificación + constantes |
| `app/src/main/java/com/jobalert/service/AlertNotificationListenerService.kt` | Modificar — lógica foreground |
| `app/src/main/java/com/jobalert/ui/permissions/PermissionsScreen.kt` | Modificar — card de batería |

---

## Task 1: Drawable del ícono de notificación

**Files:**
- Create: `app/src/main/res/drawable/ic_notification.xml`

- [ ] **Paso 1: Crear el vector drawable**

  El small icon de la barra de estado debe ser monocromático. Crear `app/src/main/res/drawable/ic_notification.xml` con este contenido exacto:

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="24dp"
      android:height="24dp"
      android:viewportWidth="24"
      android:viewportHeight="24">
      <path
          android:fillColor="@android:color/white"
          android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z" />
  </vector>
  ```

- [ ] **Paso 2: Verificar que compila**

  ```bash
  ./gradlew :app:assembleDebug
  ```
  Esperado: `BUILD SUCCESSFUL`

- [ ] **Paso 3: Commit**

  ```bash
  git add app/src/main/res/drawable/ic_notification.xml
  git commit -m "feat: add notification bell icon drawable"
  ```

---

## Task 2: Permisos y tipo de foreground service en el Manifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Paso 1: Agregar los tres permisos nuevos**

  Añadir justo después de `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`:

  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
  <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
  ```

- [ ] **Paso 2: Agregar `foregroundServiceType` al servicio**

  El bloque `<service>` debe quedar:

  ```xml
  <service
      android:name=".service.AlertNotificationListenerService"
      android:exported="true"
      android:label="@string/app_name"
      android:foregroundServiceType="specialUse"
      android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
      <intent-filter>
          <action android:name="android.service.notification.NotificationListenerService" />
      </intent-filter>
  </service>
  ```

- [ ] **Paso 3: Verificar que compila**

  ```bash
  ./gradlew :app:assembleDebug
  ```
  Esperado: `BUILD SUCCESSFUL`

- [ ] **Paso 4: Commit**

  ```bash
  git add app/src/main/AndroidManifest.xml
  git commit -m "feat: add foreground service permissions and type to manifest"
  ```

---

## Task 3: Test para `getUnacknowledgedCount` (TDD — escribir primero)

**Files:**
- Modify: `app/src/androidTest/java/com/jobalert/data/AlertDaoTest.kt`

- [ ] **Paso 1: Agregar el test que fallará**

  En `AlertDaoTest.kt`, añadir este test al final de la clase (antes del `}`):

  ```kotlin
  @Test
  fun getUnacknowledgedCount_reflectsAcknowledgedState() = runBlocking {
      dao.insert(AlertEntity(timestamp = 1000L, sender = "a@b.com", subject = "S1", snippet = ""))
      val id2 = dao.insert(AlertEntity(timestamp = 2000L, sender = "a@b.com", subject = "S2", snippet = ""))
      dao.insert(AlertEntity(timestamp = 3000L, sender = "a@b.com", subject = "S3", snippet = ""))

      assertEquals(3, dao.getUnacknowledgedCount().first())

      dao.acknowledge(id2)
      assertEquals(2, dao.getUnacknowledgedCount().first())
  }
  ```

- [ ] **Paso 2: Verificar que el test no compila aún** (falta la función en el DAO)

  ```bash
  ./gradlew :app:assembleDebugAndroidTest
  ```
  Esperado: error de compilación — `Unresolved reference: getUnacknowledgedCount`

---

## Task 4: `AlertDao` + `AlertRepository` — conteo de no atendidas

**Files:**
- Modify: `app/src/main/java/com/jobalert/data/db/AlertDao.kt`
- Modify: `app/src/main/java/com/jobalert/data/repository/AlertRepository.kt`

- [ ] **Paso 1: Agregar la query en `AlertDao`**

  Añadir al final de la interfaz (antes del `}`):

  ```kotlin
  @Query("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0")
  fun getUnacknowledgedCount(): Flow<Int>
  ```

- [ ] **Paso 2: Agregar la propiedad en `AlertRepository`**

  Añadir después de `val allAlerts`:

  ```kotlin
  val unacknowledgedCount: Flow<Int> = dao.getUnacknowledgedCount()
  ```

- [ ] **Paso 3: Ejecutar el test instrumentado** (requiere dispositivo/emulador conectado)

  ```bash
  ./gradlew :app:connectedDebugAndroidTest --tests "com.jobalert.data.AlertDaoTest.getUnacknowledgedCount_reflectsAcknowledgedState"
  ```
  Esperado: `PASSED`

- [ ] **Paso 4: Commit**

  ```bash
  git add app/src/main/java/com/jobalert/data/db/AlertDao.kt \
          app/src/main/java/com/jobalert/data/repository/AlertRepository.kt \
          app/src/androidTest/java/com/jobalert/data/AlertDaoTest.kt
  git commit -m "feat: add unacknowledged alert count query and repository flow"
  ```

---

## Task 5: `JobAlertApp` — canal de notificación y constantes

**Files:**
- Modify: `app/src/main/java/com/jobalert/JobAlertApp.kt`

- [ ] **Paso 1: Reemplazar el contenido completo del archivo**

  ```kotlin
  package com.jobalert

  import android.app.Application
  import android.app.NotificationChannel
  import android.app.NotificationManager
  import com.jobalert.data.db.AppDatabase
  import com.jobalert.data.repository.AlertRepository
  import com.jobalert.data.repository.RuleRepository
  import com.jobalert.data.repository.SettingsRepository
  import com.jobalert.overlay.OverlayManager

  class JobAlertApp : Application() {

      companion object {
          const val CHANNEL_ID = "jobalert_service"
          const val SERVICE_NOTIFICATION_ID = 1
      }

      override fun onCreate() {
          super.onCreate()
          createNotificationChannel()
      }

      private fun createNotificationChannel() {
          val channel = NotificationChannel(
              CHANNEL_ID,
              "JobAlert activo",
              NotificationManager.IMPORTANCE_LOW
          ).apply {
              description = "Mantiene el servicio de escucha de alertas activo"
          }
          getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
      }

      val database by lazy { AppDatabase.getInstance(this) }
      val alertRepository by lazy { AlertRepository(database.alertDao()) }
      val ruleRepository by lazy { RuleRepository(database.ruleDao()) }
      val settingsRepository by lazy { SettingsRepository(this) }
      val overlayManager by lazy { OverlayManager(this, settingsRepository) }
  }
  ```

- [ ] **Paso 2: Verificar que compila**

  ```bash
  ./gradlew :app:assembleDebug
  ```
  Esperado: `BUILD SUCCESSFUL`

- [ ] **Paso 3: Commit**

  ```bash
  git add app/src/main/java/com/jobalert/JobAlertApp.kt
  git commit -m "feat: create notification channel and add service notification constants"
  ```

---

## Task 6: `AlertNotificationListenerService` — lógica foreground

**Files:**
- Modify: `app/src/main/java/com/jobalert/service/AlertNotificationListenerService.kt`

- [ ] **Paso 1: Reemplazar el contenido completo del archivo**

  ```kotlin
  package com.jobalert.service

  import android.app.Notification
  import android.app.NotificationManager
  import android.app.PendingIntent
  import android.content.Intent
  import android.content.pm.ServiceInfo
  import android.os.Build
  import android.service.notification.NotificationListenerService
  import android.service.notification.StatusBarNotification
  import android.util.Log
  import androidx.core.app.NotificationCompat
  import com.jobalert.JobAlertApp
  import com.jobalert.R
  import com.jobalert.data.model.AlertEntity
  import com.jobalert.domain.NotificationData
  import com.jobalert.rules.RuleEngine
  import com.jobalert.ui.MainActivity
  import kotlinx.coroutines.CoroutineScope
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.SupervisorJob
  import kotlinx.coroutines.cancel
  import kotlinx.coroutines.launch

  private const val TAG = "JobAlert"

  class AlertNotificationListenerService : NotificationListenerService() {

      private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
      private val seenKeys = mutableSetOf<String>()
      private val notificationManager by lazy {
          getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      }

      private val app get() = application as JobAlertApp

      override fun onListenerConnected() {
          super.onListenerConnected()
          Log.i(TAG, "SERVICE CONNECTED — escuchando notificaciones")
          startForegroundCompat(buildServiceNotification(0))
          serviceScope.launch {
              app.alertRepository.unacknowledgedCount.collect { count ->
                  notificationManager.notify(
                      JobAlertApp.SERVICE_NOTIFICATION_ID,
                      buildServiceNotification(count)
                  )
              }
          }
      }

      override fun onListenerDisconnected() {
          super.onListenerDisconnected()
          Log.w(TAG, "SERVICE DISCONNECTED — se perdió el enlace con el sistema")
      }

      private fun startForegroundCompat(notification: Notification) {
          if (Build.VERSION.SDK_INT >= 34) {
              startForeground(
                  JobAlertApp.SERVICE_NOTIFICATION_ID,
                  notification,
                  ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
              )
          } else {
              startForeground(JobAlertApp.SERVICE_NOTIFICATION_ID, notification)
          }
      }

      private fun buildServiceNotification(unacknowledgedCount: Int): Notification {
          val intent = Intent(this, MainActivity::class.java).apply {
              flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
          }
          val pendingIntent = PendingIntent.getActivity(
              this, 0, intent, PendingIntent.FLAG_IMMUTABLE
          )
          val text = if (unacknowledgedCount == 0)
              "Escuchando notificaciones de Gmail"
          else
              "$unacknowledgedCount alerta(s) sin atender"

          return NotificationCompat.Builder(this, JobAlertApp.CHANNEL_ID)
              .setSmallIcon(R.drawable.ic_notification)
              .setContentTitle("JobAlert activo")
              .setContentText(text)
              .setContentIntent(pendingIntent)
              .setOngoing(true)
              .setPriority(NotificationCompat.PRIORITY_LOW)
              .build()
      }

      override fun onNotificationPosted(sbn: StatusBarNotification) {
          Log.d(TAG, "[1] onNotificationPosted: pkg=${sbn.packageName} key=${sbn.key}")

          if (sbn.packageName != "com.google.android.gm") {
              Log.d(TAG, "[1] SKIP: no es Gmail (pkg=${sbn.packageName})")
              return
          }

          if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
              Log.d(TAG, "[2] SKIP: es notificación de resumen de grupo (FLAG_GROUP_SUMMARY)")
              return
          }

          if (sbn.key in seenKeys) {
              Log.d(TAG, "[3] SKIP: clave ya procesada key=${sbn.key}")
              return
          }
          seenKeys.add(sbn.key)

          val extras = sbn.notification.extras
          val sender = extras.getString(Notification.EXTRA_TITLE) ?: ""
          val rawSubject = extras.getString(Notification.EXTRA_TEXT) ?: ""
          val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
          val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
              ?.joinToString(" ") { it.toString() }

          // Gmail a veces deja EXTRA_TEXT vacío y pone "asunto\ncuerpo" en EXTRA_BIG_TEXT
          val subject = if (rawSubject.isNotBlank()) {
              rawSubject
          } else {
              bigText?.lines()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: ""
          }
          val snippet = bigText ?: textLines ?: subject

          Log.i(TAG, "[4] Gmail recibido — sender='$sender' | subject='$subject' | snippet='${snippet.take(80)}'")

          val data = NotificationData(sender = sender, subject = subject, snippet = snippet)

          serviceScope.launch {
              val rules = app.ruleRepository.getAllRulesOnce()
              Log.d(TAG, "[5] Reglas cargadas: ${rules.size} regla(s) — $rules")
              val matched = RuleEngine.match(data, rules)
              Log.i(TAG, "[5] RuleEngine.match=$matched")
              if (matched) {
                  Log.i(TAG, "[6] ALERTA DISPARADA — mostrando overlay")
                  app.alertRepository.insert(
                      AlertEntity(
                          timestamp = System.currentTimeMillis(),
                          sender = sender,
                          subject = subject,
                          snippet = snippet
                      )
                  )
                  app.overlayManager.show(data)
              } else {
                  Log.i(TAG, "[5] Ninguna regla coincidió — sin alerta")
              }
          }
      }

      override fun onNotificationRemoved(sbn: StatusBarNotification) {
          seenKeys.remove(sbn.key)
      }

      override fun onDestroy() {
          super.onDestroy()
          serviceScope.cancel()
          seenKeys.clear()
      }
  }
  ```

- [ ] **Paso 2: Verificar que compila**

  ```bash
  ./gradlew :app:assembleDebug
  ```
  Esperado: `BUILD SUCCESSFUL`

- [ ] **Paso 3: Commit**

  ```bash
  git add app/src/main/java/com/jobalert/service/AlertNotificationListenerService.kt
  git commit -m "feat: start foreground service and observe unacknowledged count in notification"
  ```

---

## Task 7: `PermissionsScreen` — card de optimización de batería

**Files:**
- Modify: `app/src/main/java/com/jobalert/ui/permissions/PermissionsScreen.kt`

- [ ] **Paso 1: Reemplazar el contenido completo del archivo**

  ```kotlin
  package com.jobalert.ui.permissions

  import android.content.Context
  import android.content.Intent
  import android.net.Uri
  import android.os.PowerManager
  import android.provider.Settings
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.width
  import androidx.compose.material3.Button
  import androidx.compose.material3.Card
  import androidx.compose.material3.CardDefaults
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.OutlinedButton
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.DisposableEffect
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.unit.dp
  import androidx.lifecycle.Lifecycle
  import androidx.lifecycle.LifecycleEventObserver
  import androidx.compose.ui.platform.LocalLifecycleOwner
  import com.jobalert.JobAlertApp
  import com.jobalert.domain.NotificationData

  @Composable
  fun PermissionsScreen() {
      val context = LocalContext.current
      val lifecycleOwner = LocalLifecycleOwner.current

      var hasNotificationAccess by remember { mutableStateOf(false) }
      var hasOverlayPermission by remember { mutableStateOf(false) }
      var hasBatteryExemption by remember { mutableStateOf(false) }

      DisposableEffect(lifecycleOwner) {
          val observer = LifecycleEventObserver { _, event ->
              if (event == Lifecycle.Event.ON_RESUME) {
                  hasNotificationAccess = isNotificationListenerEnabled(context)
                  hasOverlayPermission = Settings.canDrawOverlays(context)
                  val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                  hasBatteryExemption = pm.isIgnoringBatteryOptimizations(context.packageName)
              }
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

      Column(
          modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
          Text("Estado y permisos", style = MaterialTheme.typography.headlineSmall)

          PermissionRow(
              label = "Acceso a notificaciones",
              description = "Permite leer notificaciones de Gmail",
              granted = hasNotificationAccess,
              onGrant = {
                  context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
              }
          )

          PermissionRow(
              label = "Mostrar sobre otras apps",
              description = "Permite la superposición a pantalla completa",
              granted = hasOverlayPermission,
              onGrant = {
                  context.startActivity(
                      Intent(
                          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                          Uri.parse("package:${context.packageName}")
                      )
                  )
              }
          )

          PermissionRow(
              label = "Sin restricción de batería",
              description = "Evita que Android detenga el servicio cuando el teléfono está inactivo",
              granted = hasBatteryExemption,
              onGrant = {
                  context.startActivity(
                      Intent(
                          Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                          Uri.parse("package:${context.packageName}")
                      )
                  )
              }
          )

          if (hasNotificationAccess && hasOverlayPermission && hasBatteryExemption) {
              Spacer(Modifier.height(8.dp))
              Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
              ) {
                  Text(
                      text = "Todo listo. El servicio escucha notificaciones de Gmail.",
                      modifier = Modifier.padding(16.dp),
                      style = MaterialTheme.typography.bodyMedium
                  )
              }
          }

          Spacer(Modifier.weight(1f))

          val app = context.applicationContext as JobAlertApp
          OutlinedButton(
              onClick = {
                  app.overlayManager.show(
                      NotificationData(
                          sender = "alertas@sistema.com",
                          subject = "CRITICAL: Servidor de prueba caído",
                          snippet = "Este es un aviso de prueba generado manualmente desde JobAlert."
                      )
                  )
              },
              modifier = Modifier.fillMaxWidth()
          ) {
              Text("Probar alerta (demo)")
          }
      }
  }

  @Composable
  private fun PermissionRow(
      label: String,
      description: String,
      granted: Boolean,
      onGrant: () -> Unit
  ) {
      Card(modifier = Modifier.fillMaxWidth()) {
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
          ) {
              Column(modifier = Modifier.weight(1f)) {
                  Text(label, style = MaterialTheme.typography.titleSmall)
                  Text(description, style = MaterialTheme.typography.bodySmall)
                  Text(
                      text = if (granted) "Concedido" else "Pendiente",
                      color = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
                      style = MaterialTheme.typography.labelMedium
                  )
              }
              if (!granted) {
                  Spacer(Modifier.width(8.dp))
                  Button(onClick = onGrant) { Text("Conceder") }
              }
          }
      }
  }

  private fun isNotificationListenerEnabled(context: Context): Boolean {
      val listeners = Settings.Secure.getString(
          context.contentResolver,
          "enabled_notification_listeners"
      ) ?: return false
      return listeners.contains(context.packageName)
  }
  ```

- [ ] **Paso 2: Verificar que compila**

  ```bash
  ./gradlew :app:assembleDebug
  ```
  Esperado: `BUILD SUCCESSFUL`

- [ ] **Paso 3: Commit**

  ```bash
  git add app/src/main/java/com/jobalert/ui/permissions/PermissionsScreen.kt
  git commit -m "feat: add battery optimization exemption permission card"
  ```

---

## Task 8: Instalar y verificar manualmente

- [ ] **Paso 1: Instalar el APK**

  ```bash
  ./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

- [ ] **Paso 2: Verificar notificación persistente**

  Abrir la app → pantalla "Estado y permisos" → conceder los tres permisos (incluyendo "Sin restricción de batería") → verificar que aparece en la barra de estado la notificación **"JobAlert activo — Escuchando notificaciones de Gmail"**.

- [ ] **Paso 3: Verificar actualización de conteo**

  Recibir un correo que coincida con una regla activa → el overlay aparece → sin tocarlo, deslizar la barra de estado → la notificación debe decir **"1 alerta(s) sin atender"**. Tocar "ATENDIDO" → la notificación vuelve a **"Escuchando notificaciones de Gmail"**.

- [ ] **Paso 4: Verificar supervivencia con fuerza bruta**

  ```bash
  adb shell am kill com.jobalert
  ```
  Esperar 10 segundos → la notificación persistente debe reaparecer (el sistema reinicia el `NotificationListenerService`).

- [ ] **Paso 5: Verificar log de conexión**

  ```bash
  adb logcat -s JobAlert:I
  ```
  Esperado tras reinstalar: `SERVICE CONNECTED — escuchando notificaciones`
