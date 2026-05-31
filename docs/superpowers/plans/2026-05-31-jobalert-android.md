# JobAlert Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App Android nativa (Kotlin + Compose) que escucha notificaciones de Gmail, aplica reglas configurables (remitente + keywords) y muestra una superposición a pantalla completa que exige confirmación para cerrarse.

**Architecture:** `NotificationListenerService` detecta notificaciones de `com.google.android.gm`, el `RuleEngine` evalúa las reglas almacenadas en Room, y el `OverlayManager` dibuja un `ComposeView` a pantalla completa vía `WindowManager.TYPE_APPLICATION_OVERLAY`. Todo corre en el dispositivo, sin servidor.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Navigation Compose, ViewModel, Coroutines, DataStore, Gson, JUnit4.

---

## File Map

```
jobalert/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/
│       │   │   ├── drawable/ic_launcher_background.xml
│       │   │   ├── drawable/ic_launcher_foreground.xml
│       │   │   ├── mipmap-anydpi-v26/ic_launcher.xml
│       │   │   ├── mipmap-anydpi-v26/ic_launcher_round.xml
│       │   │   ├── values/strings.xml
│       │   │   └── values/themes.xml
│       │   └── java/com/jobalert/
│       │       ├── JobAlertApp.kt
│       │       ├── domain/
│       │       │   ├── NotificationData.kt        # pure data, no Android deps
│       │       │   └── Rule.kt                    # Room entity + domain model
│       │       ├── data/
│       │       │   ├── db/
│       │       │   │   ├── Converters.kt          # List<String> ↔ JSON
│       │       │   │   ├── AlertDao.kt
│       │       │   │   ├── RuleDao.kt
│       │       │   │   └── AppDatabase.kt
│       │       │   ├── model/AlertEntity.kt
│       │       │   └── repository/
│       │       │       ├── AlertRepository.kt
│       │       │       ├── RuleRepository.kt
│       │       │       └── SettingsRepository.kt  # SharedPreferences wrapper
│       │       ├── rules/RuleEngine.kt            # pure Kotlin, JVM-testable
│       │       ├── service/AlertNotificationListenerService.kt
│       │       ├── overlay/
│       │       │   ├── ServiceLifecycleOwner.kt   # LifecycleOwner para ComposeView en overlay
│       │       │   └── OverlayManager.kt
│       │       └── ui/
│       │           ├── MainActivity.kt
│       │           ├── AppNavigation.kt
│       │           ├── permissions/PermissionsScreen.kt
│       │           ├── rules/
│       │           │   ├── RulesViewModel.kt
│       │           │   └── RulesScreen.kt
│       │           ├── history/
│       │           │   ├── HistoryViewModel.kt
│       │           │   └── HistoryScreen.kt
│       │           └── settings/
│       │               ├── SettingsViewModel.kt
│       │               └── SettingsScreen.kt
│       ├── test/java/com/jobalert/rules/RuleEngineTest.kt
│       └── androidTest/java/com/jobalert/data/AlertDaoTest.kt
```

---

## Task 1: Gradle Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `local.properties`

- [ ] **Step 1: Create directory structure**

```bash
cd /home/jatauje/jobalert
mkdir -p gradle app/src/main/java/com/jobalert app/src/main/res/drawable \
  app/src/main/res/mipmap-anydpi-v26 app/src/main/res/values \
  app/src/main/res/xml \
  app/src/test/java/com/jobalert/rules \
  app/src/androidTest/java/com/jobalert/data
```

- [ ] **Step 2: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "JobAlert"
include(":app")
```

- [ ] **Step 3: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.2.2"
kotlin = "1.9.22"
composeBom = "2024.02.00"
activityCompose = "1.8.2"
navigationCompose = "2.7.6"
lifecycle = "2.7.0"
room = "2.6.1"
coroutines = "1.7.3"
gson = "2.10.1"
junit = "4.13.2"
junitExt = "1.1.5"
testRunner = "1.5.2"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
junit-ext = { group = "androidx.test.ext", name = "junit", version.ref = "junitExt" }
test-runner = { group = "androidx.test", name = "runner", version.ref = "testRunner" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

- [ ] **Step 5: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.jobalert"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jobalert"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)
    // kapt for Room code generation
    configurations.all {
        resolutionStrategy.force("androidx.room:room-compiler:2.6.1")
    }

    implementation(libs.coroutines.android)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
}
```

> **Note:** Room requires kapt or KSP for code generation. Replace `annotationProcessor` with `kapt` by adding `kotlin("kapt")` to plugins and using `kapt(libs.room.compiler)`. Update `app/build.gradle.kts`:
>
> ```kotlin
> plugins {
>     alias(libs.plugins.android.application)
>     alias(libs.plugins.kotlin.android)
>     kotlin("kapt")
> }
> // ... in dependencies:
>     kapt(libs.room.compiler)
> // remove annotationProcessor and configurations.all block
> ```

- [ ] **Step 6: Create `app/proguard-rules.pro`** (vacío es suficiente para debug)

```
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
```

- [ ] **Step 7: Create `local.properties`** — ajusta la ruta al SDK de Android en tu máquina

```properties
sdk.dir=/home/jatauje/Android/Sdk
```

- [ ] **Step 8: Initialize Gradle wrapper**

```bash
cd /home/jatauje/jobalert
gradle wrapper --gradle-version 8.5
```

Expected: genera `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

Si `gradle` no está en PATH: `sdk install gradle 8.5` con sdkman, o descargar manualmente de https://gradle.org/releases/.

---

## Task 2: Resources y AndroidManifest

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Crear `res/drawable/ic_launcher_background.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#CC0000" />
</shape>
```

- [ ] **Step 2: Crear `res/drawable/ic_launcher_foreground.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="8"
        android:pathData="M54,30 L54,78 M30,54 L78,54" />
</vector>
```

- [ ] **Step 3: Crear `res/mipmap-anydpi-v26/ic_launcher.xml` y `ic_launcher_round.xml`** (mismo contenido en ambos)

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 4: Crear `res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">JobAlert</string>
</resources>
```

- [ ] **Step 5: Crear `res/values/themes.xml`**

```xml
<resources>
    <style name="Theme.JobAlert" parent="android:Theme.DeviceDefault.NoActionBar" />
</resources>
```

- [ ] **Step 6: Crear `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".JobAlertApp"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.JobAlert">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.AlertNotificationListenerService"
            android:exported="true"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

---

## Task 3: Domain Models

**Files:**
- Create: `app/src/main/java/com/jobalert/domain/NotificationData.kt`
- Create: `app/src/main/java/com/jobalert/domain/Rule.kt`

- [ ] **Step 1: Crear `NotificationData.kt`** — clase pura, sin dependencias de Android

```kotlin
package com.jobalert.domain

data class NotificationData(
    val sender: String,
    val subject: String,
    val snippet: String
)
```

- [ ] **Step 2: Crear `Rule.kt`** — sirve como entidad de Room y como modelo de dominio

```kotlin
package com.jobalert.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val senders: List<String>,   // vacío = coincide con cualquier remitente
    val keywords: List<String>   // vacío = coincide con cualquier contenido
)
```

---

## Task 4: RuleEngine + Unit Tests

**Files:**
- Create: `app/src/main/java/com/jobalert/rules/RuleEngine.kt`
- Create: `app/src/test/java/com/jobalert/rules/RuleEngineTest.kt`

- [ ] **Step 1: Escribir el test (primero, sin implementación)**

Archivo: `app/src/test/java/com/jobalert/rules/RuleEngineTest.kt`

```kotlin
package com.jobalert.rules

import com.jobalert.domain.NotificationData
import com.jobalert.domain.Rule
import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {

    private val data = NotificationData(
        sender = "alertas@sistema.com",
        subject = "CRITICAL: Server DOWN",
        snippet = "El servidor production-01 no responde desde las 03:00"
    )

    @Test
    fun `matches when sender substring and keyword both match`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("sistema.com"), keywords = listOf("CRITICAL")))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `does not match when sender does not match any in list`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("otro.com"), keywords = listOf("CRITICAL")))
        assertFalse(RuleEngine.match(data, rules))
    }

    @Test
    fun `does not match when no keyword found in subject or snippet`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("sistema.com"), keywords = listOf("WARNING")))
        assertFalse(RuleEngine.match(data, rules))
    }

    @Test
    fun `match is case insensitive for sender and keyword`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("SISTEMA.COM"), keywords = listOf("critical")))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `matches keyword found in snippet even if not in subject`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("sistema.com"), keywords = listOf("production-01")))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `empty rules list never matches`() {
        assertFalse(RuleEngine.match(data, emptyList()))
    }

    @Test
    fun `empty sender list matches any sender`() {
        val rules = listOf(Rule(name = "Any", senders = emptyList(), keywords = listOf("CRITICAL")))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `empty keyword list matches any content`() {
        val rules = listOf(Rule(name = "Any", senders = listOf("sistema.com"), keywords = emptyList()))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `second rule matches when first rule does not`() {
        val rules = listOf(
            Rule(name = "Rule1", senders = listOf("otro.com"), keywords = listOf("CRITICAL")),
            Rule(name = "Rule2", senders = listOf("sistema.com"), keywords = listOf("CRITICAL"))
        )
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `does not match sender with empty keywords if rule has keywords`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("sistema.com"), keywords = listOf("NEVER_HERE")))
        assertFalse(RuleEngine.match(data, rules))
    }
}
```

- [ ] **Step 2: Verificar que el test falla (no existe `RuleEngine` aún)**

```bash
cd /home/jatauje/jobalert
./gradlew :app:testDebugUnitTest --tests "com.jobalert.rules.RuleEngineTest" 2>&1 | tail -20
```

Expected: `FAILED` con error de compilación "Unresolved reference: RuleEngine".

- [ ] **Step 3: Implementar `RuleEngine.kt`**

```kotlin
package com.jobalert.rules

import com.jobalert.domain.NotificationData
import com.jobalert.domain.Rule

object RuleEngine {

    /**
     * Devuelve true si [data] coincide con al menos una regla en [rules].
     *
     * Semántica de una regla:
     * - senders vacío → acepta cualquier remitente
     * - keywords vacío → acepta cualquier contenido
     * - senders no vacío → al menos un elemento de la lista debe ser substring
     *   del remitente (case-insensitive)
     * - keywords no vacío → al menos una keyword debe aparecer en el asunto
     *   O en el snippet (case-insensitive)
     */
    fun match(data: NotificationData, rules: List<Rule>): Boolean {
        if (rules.isEmpty()) return false
        return rules.any { rule -> matchesRule(data, rule) }
    }

    private fun matchesRule(data: NotificationData, rule: Rule): Boolean {
        val senderMatches = rule.senders.isEmpty() ||
            rule.senders.any { data.sender.contains(it, ignoreCase = true) }

        val keywordMatches = rule.keywords.isEmpty() ||
            rule.keywords.any { kw ->
                data.subject.contains(kw, ignoreCase = true) ||
                    data.snippet.contains(kw, ignoreCase = true)
            }

        return senderMatches && keywordMatches
    }
}
```

- [ ] **Step 4: Verificar que los tests pasan**

```bash
./gradlew :app:testDebugUnitTest --tests "com.jobalert.rules.RuleEngineTest"
```

Expected: `BUILD SUCCESSFUL` con 10 tests en verde.

- [ ] **Step 5: Commit**

```bash
git init
git add app/src/main/java/com/jobalert/rules/RuleEngine.kt \
        app/src/main/java/com/jobalert/domain/ \
        app/src/test/java/com/jobalert/rules/RuleEngineTest.kt
git commit -m "feat: add RuleEngine with unit tests"
```

---

## Task 5: Room Database (Converters, DAOs, AppDatabase)

**Files:**
- Create: `app/src/main/java/com/jobalert/data/model/AlertEntity.kt`
- Create: `app/src/main/java/com/jobalert/data/db/Converters.kt`
- Create: `app/src/main/java/com/jobalert/data/db/AlertDao.kt`
- Create: `app/src/main/java/com/jobalert/data/db/RuleDao.kt`
- Create: `app/src/main/java/com/jobalert/data/db/AppDatabase.kt`

- [ ] **Step 1: Crear `AlertEntity.kt`**

```kotlin
package com.jobalert.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val sender: String,
    val subject: String,
    val snippet: String,
    val acknowledged: Boolean = false
)
```

- [ ] **Step 2: Crear `Converters.kt`** — serializa `List<String>` como JSON para Room

```kotlin
package com.jobalert.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            gson.fromJson(value, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
```

- [ ] **Step 3: Crear `AlertDao.kt`**

```kotlin
package com.jobalert.data.db

import androidx.room.*
import com.jobalert.data.model.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Insert
    suspend fun insert(alert: AlertEntity): Long

    @Query("UPDATE alerts SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: Long)
}
```

- [ ] **Step 4: Crear `RuleDao.kt`**

```kotlin
package com.jobalert.data.db

import androidx.room.*
import com.jobalert.domain.Rule
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY name ASC")
    fun getAllRules(): Flow<List<Rule>>

    @Query("SELECT * FROM rules ORDER BY name ASC")
    suspend fun getAllRulesOnce(): List<Rule>

    @Insert
    suspend fun insert(rule: Rule): Long

    @Delete
    suspend fun delete(rule: Rule)
}
```

- [ ] **Step 5: Crear `AppDatabase.kt`**

```kotlin
package com.jobalert.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jobalert.data.model.AlertEntity
import com.jobalert.domain.Rule

@Database(entities = [AlertEntity::class, Rule::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "jobalert.db")
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
```

---

## Task 6: Repositories + SettingsRepository

**Files:**
- Create: `app/src/main/java/com/jobalert/data/repository/AlertRepository.kt`
- Create: `app/src/main/java/com/jobalert/data/repository/RuleRepository.kt`
- Create: `app/src/main/java/com/jobalert/data/repository/SettingsRepository.kt`

- [ ] **Step 1: Crear `AlertRepository.kt`**

```kotlin
package com.jobalert.data.repository

import com.jobalert.data.db.AlertDao
import com.jobalert.data.model.AlertEntity
import kotlinx.coroutines.flow.Flow

class AlertRepository(private val dao: AlertDao) {
    val allAlerts: Flow<List<AlertEntity>> = dao.getAllAlerts()

    suspend fun insert(alert: AlertEntity): Long = dao.insert(alert)

    suspend fun acknowledge(id: Long) = dao.acknowledge(id)
}
```

- [ ] **Step 2: Crear `RuleRepository.kt`**

```kotlin
package com.jobalert.data.repository

import com.jobalert.data.db.RuleDao
import com.jobalert.domain.Rule
import kotlinx.coroutines.flow.Flow

class RuleRepository(private val dao: RuleDao) {
    val allRules: Flow<List<Rule>> = dao.getAllRules()

    suspend fun getAllRulesOnce(): List<Rule> = dao.getAllRulesOnce()

    suspend fun insert(rule: Rule): Long = dao.insert(rule)

    suspend fun delete(rule: Rule) = dao.delete(rule)
}
```

- [ ] **Step 3: Crear `SettingsRepository.kt`**

```kotlin
package com.jobalert.data.repository

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jobalert_settings", Context.MODE_PRIVATE)

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", false)
        set(value) { prefs.edit().putBoolean("sound_enabled", value).apply() }
}
```

- [ ] **Step 4: Crear `JobAlertApp.kt`** — Application class que centraliza las dependencias

```kotlin
package com.jobalert

import android.app.Application
import com.jobalert.data.db.AppDatabase
import com.jobalert.data.repository.AlertRepository
import com.jobalert.data.repository.RuleRepository
import com.jobalert.data.repository.SettingsRepository
import com.jobalert.overlay.OverlayManager

class JobAlertApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val alertRepository by lazy { AlertRepository(database.alertDao()) }
    val ruleRepository by lazy { RuleRepository(database.ruleDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val overlayManager by lazy { OverlayManager(this, settingsRepository) }
}
```

- [ ] **Step 5: Compilar para verificar que no hay errores de resolución**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:|BUILD"
```

Expected: `BUILD SUCCESSFUL` (los errores de Room annotation processor son advertencias, no errores).

---

## Task 7: AlertDao Instrumented Test

**Files:**
- Create: `app/src/androidTest/java/com/jobalert/data/AlertDaoTest.kt`

- [ ] **Step 1: Crear `AlertDaoTest.kt`**

```kotlin
package com.jobalert.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jobalert.data.db.AppDatabase
import com.jobalert.data.db.AlertDao
import com.jobalert.data.model.AlertEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: AlertDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.alertDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAlert_thenRetrieveIt() = runBlocking {
        val alert = AlertEntity(
            timestamp = 1000L,
            sender = "test@ejemplo.com",
            subject = "CRITICAL: Servidor caído",
            snippet = "El servidor no responde"
        )
        val insertedId = dao.insert(alert)

        val alerts = dao.getAllAlerts().first()
        assertEquals(1, alerts.size)
        assertEquals(insertedId, alerts[0].id)
        assertEquals("CRITICAL: Servidor caído", alerts[0].subject)
        assertFalse(alerts[0].acknowledged)
    }

    @Test
    fun acknowledgeAlert_updatesFlag() = runBlocking {
        val id = dao.insert(
            AlertEntity(timestamp = 2000L, sender = "a@b.com", subject = "S", snippet = "N")
        )
        dao.acknowledge(id)

        val alerts = dao.getAllAlerts().first()
        assertTrue(alerts[0].acknowledged)
    }

    @Test
    fun alerts_orderedByTimestampDesc() = runBlocking {
        dao.insert(AlertEntity(timestamp = 1000L, sender = "a@b.com", subject = "Primero", snippet = ""))
        dao.insert(AlertEntity(timestamp = 3000L, sender = "a@b.com", subject = "Tercero", snippet = ""))
        dao.insert(AlertEntity(timestamp = 2000L, sender = "a@b.com", subject = "Segundo", snippet = ""))

        val alerts = dao.getAllAlerts().first()
        assertEquals("Tercero", alerts[0].subject)
        assertEquals("Segundo", alerts[1].subject)
        assertEquals("Primero", alerts[2].subject)
    }
}
```

- [ ] **Step 2: Ejecutar el test en dispositivo/emulador conectado**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.jobalert.data.AlertDaoTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` con 3 tests en verde.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/jobalert/data/ \
        app/src/main/java/com/jobalert/JobAlertApp.kt \
        app/src/androidTest/
git commit -m "feat: add Room database, repositories, and DAO tests"
```

---

## Task 8: ServiceLifecycleOwner + OverlayManager

**Files:**
- Create: `app/src/main/java/com/jobalert/overlay/ServiceLifecycleOwner.kt`
- Create: `app/src/main/java/com/jobalert/overlay/OverlayManager.kt`

**Contexto:** `ComposeView` necesita un `LifecycleOwner` para gestionar su composición. En un `Service` no existe uno, así que creamos el nuestro. `OverlayManager` crea una ventana a pantalla completa con `WindowManager.TYPE_APPLICATION_OVERLAY` y pone un `ComposeView` dentro.

- [ ] **Step 1: Crear `ServiceLifecycleOwner.kt`**

```kotlin
package com.jobalert.overlay

import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
    }
}
```

- [ ] **Step 2: Crear `OverlayManager.kt`**

```kotlin
package com.jobalert.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.jobalert.data.repository.SettingsRepository
import com.jobalert.domain.NotificationData
import java.text.SimpleDateFormat
import java.util.*

class OverlayManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentView: ComposeView? = null
    private var currentOwner: ServiceLifecycleOwner? = null
    private val queue: ArrayDeque<NotificationData> = ArrayDeque()

    fun show(data: NotificationData) {
        if (currentView != null) {
            queue.addLast(data)
            return
        }
        showInternal(data)
    }

    private fun showInternal(data: NotificationData) {
        val owner = ServiceLifecycleOwner().also { it.start() }
        currentOwner = owner

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                MaterialTheme {
                    AlertOverlayContent(data = data, onAcknowledge = { dismiss() })
                }
            }
        }

        windowManager.addView(view, params)
        currentView = view

        if (settingsRepository.soundEnabled) playSound()
    }

    fun dismiss() {
        stopSound()
        currentView?.let { windowManager.removeView(it) }
        currentView = null
        currentOwner?.destroy()
        currentOwner = null
        queue.removeFirstOrNull()?.let { showInternal(it) }
    }

    private var ringtone: android.media.Ringtone? = null

    private fun playSound() {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone?.play()
    }

    private fun stopSound() {
        ringtone?.stop()
        ringtone = null
    }
}

@Composable
fun AlertOverlayContent(data: NotificationData, onAcknowledge: () -> Unit) {
    val timeStr = remember(data) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFB71C1C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🚨 ALERTA",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = timeStr,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "De: ${data.sender}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = data.subject,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (data.snippet.isNotBlank() && data.snippet != data.subject) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = data.snippet,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onAcknowledge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    text = "✓  ATENDIDO",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFB71C1C)
                )
            }
        }
    }
}
```

> **Nota de import:** `remember` necesita `import androidx.compose.runtime.remember`. Añadir el import si el compilador lo requiere.

---

## Task 9: AlertNotificationListenerService

**Files:**
- Create: `app/src/main/java/com/jobalert/service/AlertNotificationListenerService.kt`

- [ ] **Step 1: Crear `AlertNotificationListenerService.kt`**

```kotlin
package com.jobalert.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jobalert.JobAlertApp
import com.jobalert.data.model.AlertEntity
import com.jobalert.domain.NotificationData
import com.jobalert.rules.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AlertNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val seenKeys = mutableSetOf<String>()   // evita disparar dos veces la misma notif

    private val app get() = application as JobAlertApp

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Solo notificaciones de Gmail
        if (sbn.packageName != "com.google.android.gm") return

        // Ignorar la notificación-resumen de grupo
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Deduplicar por clave
        if (sbn.key in seenKeys) return
        seenKeys.add(sbn.key)

        val extras = sbn.notification.extras
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val subject = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val snippet = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString(" ") { it.toString() }
            ?: subject

        val data = NotificationData(sender = sender, subject = subject, snippet = snippet)

        serviceScope.launch {
            val rules = app.ruleRepository.getAllRulesOnce()
            if (RuleEngine.match(data, rules)) {
                app.alertRepository.insert(
                    AlertEntity(
                        timestamp = System.currentTimeMillis(),
                        sender = sender,
                        subject = subject,
                        snippet = snippet
                    )
                )
                app.overlayManager.show(data)
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

- [ ] **Step 2: Compilar**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "^e:|BUILD"
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 10: PermissionsScreen

**Files:**
- Create: `app/src/main/java/com/jobalert/ui/permissions/PermissionsScreen.kt`

- [ ] **Step 1: Crear `PermissionsScreen.kt`**

```kotlin
package com.jobalert.ui.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jobalert.JobAlertApp
import com.jobalert.domain.NotificationData

@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotificationAccess by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }

    // Actualiza el estado de permisos cada vez que la pantalla vuelve al foco
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = isNotificationListenerEnabled(context)
                hasOverlayPermission = Settings.canDrawOverlays(context)
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

        if (hasNotificationAccess && hasOverlayPermission) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = "✓  Todo listo. El servicio escucha notificaciones de Gmail.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Botón de prueba — dispara la superposición con datos de ejemplo
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
                    text = if (granted) "✓ Concedido" else "✗ Pendiente",
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

private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val listeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return listeners.contains(context.packageName)
}
```

---

## Task 11: RulesScreen + RulesViewModel

**Files:**
- Create: `app/src/main/java/com/jobalert/ui/rules/RulesViewModel.kt`
- Create: `app/src/main/java/com/jobalert/ui/rules/RulesScreen.kt`

- [ ] **Step 1: Crear `RulesViewModel.kt`**

```kotlin
package com.jobalert.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jobalert.data.repository.RuleRepository
import com.jobalert.domain.Rule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(private val repository: RuleRepository) : ViewModel() {
    val rules: StateFlow<List<Rule>> = repository.allRules
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addRule(name: String, senders: List<String>, keywords: List<String>) {
        viewModelScope.launch {
            repository.insert(Rule(name = name, senders = senders, keywords = keywords))
        }
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch { repository.delete(rule) }
    }
}
```

- [ ] **Step 2: Crear `RulesScreen.kt`**

```kotlin
package com.jobalert.ui.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jobalert.JobAlertApp
import com.jobalert.domain.Rule

@Composable
fun RulesScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as JobAlertApp
    val viewModel: RulesViewModel = viewModel(
        factory = viewModelFactory { initializer { RulesViewModel(app.ruleRepository) } }
    )

    val rules by viewModel.rules.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva regla")
            }
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Sin reglas. Toca + para agregar una.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(rule = rule, onDelete = { viewModel.deleteRule(rule) })
                }
            }
        }
    }

    if (showDialog) {
        AddRuleDialog(
            onConfirm = { name, senders, keywords ->
                viewModel.addRule(name, senders, keywords)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun RuleCard(rule: Rule, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall)
                if (rule.senders.isNotEmpty()) {
                    Text(
                        "Remitentes: ${rule.senders.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Remitentes: cualquiera", style = MaterialTheme.typography.bodySmall)
                }
                if (rule.keywords.isNotEmpty()) {
                    Text(
                        "Keywords: ${rule.keywords.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Keywords: cualquiera", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar regla")
            }
        }
    }
}

@Composable
private fun AddRuleDialog(
    onConfirm: (name: String, senders: List<String>, keywords: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sendersText by remember { mutableStateOf("") }
    var keywordsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva regla") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la regla") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sendersText,
                    onValueChange = { sendersText = it },
                    label = { Text("Remitentes (separados por coma)") },
                    placeholder = { Text("ej: alertas@sistema.com, ops@") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    label = { Text("Keywords (separadas por coma)") },
                    placeholder = { Text("ej: CRITICAL, DOWN, FALLO") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Vacío = coincide con cualquiera",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val senders = sendersText.split(",")
                        .map { it.trim() }.filter { it.isNotEmpty() }
                    val keywords = keywordsText.split(",")
                        .map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(name.trim(), senders, keywords)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
```

---

## Task 12: HistoryScreen + HistoryViewModel

**Files:**
- Create: `app/src/main/java/com/jobalert/ui/history/HistoryViewModel.kt`
- Create: `app/src/main/java/com/jobalert/ui/history/HistoryScreen.kt`

- [ ] **Step 1: Crear `HistoryViewModel.kt`**

```kotlin
package com.jobalert.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jobalert.data.model.AlertEntity
import com.jobalert.data.repository.AlertRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: AlertRepository) : ViewModel() {
    val alerts: StateFlow<List<AlertEntity>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun acknowledge(id: Long) {
        viewModelScope.launch { repository.acknowledge(id) }
    }
}
```

- [ ] **Step 2: Crear `HistoryScreen.kt`**

```kotlin
package com.jobalert.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jobalert.JobAlertApp
import com.jobalert.data.model.AlertEntity
import java.text.SimpleDateFormat
import java.util.*

private val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as JobAlertApp
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory { initializer { HistoryViewModel(app.alertRepository) } }
    )

    val alerts by viewModel.alerts.collectAsState()

    if (alerts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sin alertas registradas aún.", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(alerts, key = { it.id }) { alert ->
                AlertHistoryCard(alert = alert, onAcknowledge = { viewModel.acknowledge(alert.id) })
            }
        }
    }
}

@Composable
private fun AlertHistoryCard(alert: AlertEntity, onAcknowledge: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.acknowledged)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    dateFormat.format(Date(alert.timestamp)),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    if (alert.acknowledged) "✓ Atendida" else "⚠ Pendiente",
                    color = if (alert.acknowledged) Color(0xFF2E7D32) else Color(0xFFC62828),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("De: ${alert.sender}", style = MaterialTheme.typography.labelMedium)
            Text(alert.subject, style = MaterialTheme.typography.bodyMedium)
            if (alert.snippet.isNotBlank() && alert.snippet != alert.subject) {
                Text(
                    alert.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
            if (!alert.acknowledged) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAcknowledge) {
                    Text("Marcar como atendida")
                }
            }
        }
    }
}
```

---

## Task 13: SettingsScreen + SettingsViewModel

**Files:**
- Create: `app/src/main/java/com/jobalert/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/jobalert/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Crear `SettingsViewModel.kt`**

```kotlin
package com.jobalert.ui.settings

import androidx.lifecycle.ViewModel
import com.jobalert.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _soundEnabled = MutableStateFlow(repository.soundEnabled)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    fun setSoundEnabled(enabled: Boolean) {
        repository.soundEnabled = enabled
        _soundEnabled.value = enabled
    }
}
```

- [ ] **Step 2: Crear `SettingsScreen.kt`**

```kotlin
package com.jobalert.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jobalert.JobAlertApp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as JobAlertApp
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(app.settingsRepository) } }
    )

    val soundEnabled by viewModel.soundEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Sonido de alarma", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Reproduce el sonido de alarma del sistema al recibir una alerta",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = { viewModel.setSoundEnabled(it) }
                )
            }
        }
    }
}
```

---

## Task 14: AppNavigation + MainActivity

**Files:**
- Create: `app/src/main/java/com/jobalert/ui/AppNavigation.kt`
- Create: `app/src/main/java/com/jobalert/ui/MainActivity.kt`

- [ ] **Step 1: Crear `AppNavigation.kt`**

```kotlin
package com.jobalert.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jobalert.ui.history.HistoryScreen
import com.jobalert.ui.permissions.PermissionsScreen
import com.jobalert.ui.rules.RulesScreen
import com.jobalert.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Status : Screen("status", "Estado", Icons.Default.Shield)
    object Rules : Screen("rules", "Reglas", Icons.Default.List)
    object History : Screen("history", "Historial", Icons.Default.History)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
}

private val screens = listOf(Screen.Status, Screen.Rules, Screen.History, Screen.Settings)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val current = navBackStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = current?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Status.route,
            androidx.compose.ui.Modifier.padding(paddingValues)
        ) {
            composable(Screen.Status.route) { PermissionsScreen() }
            composable(Screen.Rules.route) { RulesScreen() }
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
```

- [ ] **Step 2: Crear `MainActivity.kt`**

```kotlin
package com.jobalert.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}
```

---

## Task 15: Build final y verificación

- [ ] **Step 1: Build debug APK completo**

```bash
cd /home/jatauje/jobalert
./gradlew :app:assembleDebug 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL` — APK generado en `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Ejecutar unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`, 10 tests en verde (RuleEngineTest).

- [ ] **Step 3: Instalar en emulador/dispositivo conectado**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 4: Verificar permisos**

1. Abre JobAlert en el dispositivo.
2. Pantalla "Estado" muestra los dos permisos en rojo.
3. Toca "Conceder" para **Acceso a notificaciones** → se abre el ajuste del sistema → activa "JobAlert" → vuelve → ✓ en verde.
4. Toca "Conceder" para **Mostrar sobre otras apps** → activa → vuelve → ✓ en verde.
5. Aparece el banner "Todo listo".

- [ ] **Step 5: Probar superposición de demo**

1. Abre otra app cualquiera (ej. Chrome).
2. Ve a recientes → JobAlert → pantalla Estado → toca "Probar alerta (demo)".
3. Vuelve a Chrome.
4. Resultado esperado: la superposición roja aparece sobre Chrome con el texto de ejemplo.
5. Toca "✓ ATENDIDO" → se cierra.
6. Ve a JobAlert → Historial → aparece la alerta de prueba.

- [ ] **Step 6: Probar con correo real de Gmail**

1. Agrega una regla en la pestaña Reglas: nombre "Prueba", remitente vacío, keyword "FALLO".
2. Desde otra cuenta, envía un correo a tu cuenta de Gmail con asunto "FALLO en el sistema".
3. Cuando llegue la notificación de Gmail, la superposición debe aparecer automáticamente.
4. Verifica que un correo sin la keyword "FALLO" NO dispara la superposición.

- [ ] **Step 7: Commit final**

```bash
git add .
git commit -m "feat: complete JobAlert app — overlay, notification listener, rules, history"
```

---

## Notas de implementación

**Si `./gradlew` falla con "SDK not found":** Verifica la ruta en `local.properties`. En WSL, si el SDK está en Windows, la ruta puede ser `/mnt/c/Users/<usuario>/AppData/Local/Android/Sdk`.

**Si kapt falla:** Asegúrate de tener el plugin `kotlin("kapt")` en `app/build.gradle.kts` y usar `kapt(libs.room.compiler)` en lugar de `annotationProcessor`.

**Imports de Compose en OverlayManager:** `setViewTreeLifecycleOwner` y `setViewTreeViewModelStoreOwner` están en `androidx.lifecycle`; `setViewTreeSavedStateRegistryOwner` está en `androidx.savedstate`. Si hay conflictos de imports, usar el nombre completo calificado.

**Overlay en Android 12+:** En dispositivos con Android 12+ y ciertos OEMs, el overlay puede aparecer con bordes redondeados o con un pequeño margen del sistema. El contenido de la alerta sigue siendo completamente visible.
