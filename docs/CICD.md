# CI/CD con GitHub Actions — Guía de aprendizaje

Este documento explica, con el proyecto JobAlert como ejemplo, qué es CI/CD, cómo está
configurado aquí y cómo extenderlo. Está pensado para quien ya conoce Android y Git pero
se adentra en automatización por primera vez.

---

## 1. ¿Qué es CI/CD y por qué importa?

**CI (Continuous Integration — Integración Continua):**  
Cada vez que haces un cambio en el código, una máquina remota lo descarga, lo compila y corre
todas las validaciones (lint, tests) de forma automática. Si algo falla, te avisas *antes* de
que el error llegue a otros o a producción.

**CD (Continuous Delivery — Entrega Continua):**  
Cuando decides publicar una versión, el proceso de empaquetado, firma y publicación también es
automático. El resultado es reproducible y sin errores humanos.

**Sin CI/CD en este proyecto (situación inicial):**
- Tenías que acordarte de correr `.\gradlew lintDebug testDebugUnitTest assembleDebug` en cada cambio.
- Si un test fallaba después de un commit, lo descubrías tarde.
- Publicar un APK firmado requería pasos manuales con el keystore.

**Con CI/CD:**
- Cada `git push` dispara la validación completa automáticamente.
- Una Pull Request no puede mergearse si el build está roto (con branch protection rules).
- Crear un tag `v1.0.0` publica el release sin intervención manual.

---

## 2. Anatomía de un workflow de GitHub Actions

Un workflow es un archivo YAML en `.github/workflows/`. Repasemos la estructura con el
workflow de CI básico (`ci.yml`) como ejemplo.

```yaml
name: CI                         # Nombre visible en la pestaña "Actions" de GitHub

on:                              # Eventos que disparan el workflow
  push:
    branches: [ master ]         # Al hacer push a master
  pull_request:
    branches: [ master ]         # Al abrir/actualizar una PR hacia master

jobs:                            # Un workflow tiene uno o más jobs
  build-and-test:                # Nombre interno del job
    runs-on: ubuntu-latest       # Runner: máquina virtual donde corre el job

    steps:                       # Pasos secuenciales del job
      - name: Nombre del paso    # Etiqueta visible en la UI
        uses: actions/checkout@v4  # 'uses' ejecuta una Action publicada
        # ó
        run: ./gradlew --no-daemon :app:assembleDebug  # 'run' ejecuta shell
```

### Conceptos clave

| Concepto | Explicación |
|----------|-------------|
| **Trigger** (`on`) | Qué evento dispara el workflow: push, pull_request, tag, schedule, workflow_dispatch (manual) |
| **Job** | Unidad de trabajo que corre en un runner. Los jobs son independientes y corren en paralelo por defecto |
| **Step** | Paso dentro de un job. Los steps son secuenciales y comparten el sistema de archivos del runner |
| **Runner** | La máquina virtual donde corre el job. `ubuntu-latest` es gratis; también hay `windows-latest` y `macos-latest` |
| **Action** (`uses`) | Bloque de código reutilizable publicado en GitHub. Como una librería para workflows |
| **`run`** | Comando shell directo en el runner |
| **`if: always()`** | Condición para que un step corra incluso si los anteriores fallaron |
| **`${{ }}` expresiones** | Sintaxis para acceder a contextos: `${{ github.ref_name }}`, `${{ secrets.MI_SECRET }}` |
| **Artefacto** | Archivo subido desde el runner para descargarlo después del run (reports, APKs) |

---

## 3. Los tres workflows de JobAlert

### Fase 1: CI básico — `.github/workflows/ci.yml`

**Trigger:** cada `push` o `pull_request` a `master`.

**Qué corre:**
```
lintDebug → testDebugUnitTest → assembleDebug
```

**Por qué ese orden:** si lint o los tests fallan, no pierdas tiempo compilando.

**Comando local equivalente:**
```powershell
$env:JAVA_HOME="D:\development\android\jbr"
.\gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

**Aprende con esto:** triggers, jobs, setup-java (JDK 17), caché de Gradle, artefactos.

---

### Fase 2: Tests instrumentados — `.github/workflows/instrumented-tests.yml`

**Trigger:** cada `push` o `pull_request` a `master` (en paralelo con ci.yml).

**Qué corre:**
```
connectedDebugAndroidTest
  ├── Migration3To4Test
  ├── Migration4To5Test
  ├── Migration7To8Test
  ├── AlertDaoTest
  └── SettingsRepositoryTest
```

**Por qué necesitan emulador:**  
Estos tests usan `MigrationTestHelper` de Room (que abre una base de datos SQLite real de Android)
e `InstrumentationRegistry` — APIs que solo existen dentro del runtime de Android. La JVM de tu
computadora no las tiene.

**Trucos de CI para emuladores:**
- **KVM:** sin aceleración por hardware el emulador va 10x más lento y puede crashear.
- **Caché de AVD:** el snapshot del emulador pesa ~2 GB; cachearlo ahorra ~3 min por run.
- **`-no-window -noaudio -no-boot-anim`:** flags para modo headless (sin UI) en CI.

**Comando local equivalente:**
```bash
# Requiere un dispositivo conectado o un emulador corriendo
.\gradlew --no-daemon :app:connectedDebugAndroidTest
```

**Aprende con esto:** diferencia JVM vs instrumented tests, KVM, caché de AVD, workflows paralelos.

---

### Fase 3: CD — `.github/workflows/release.yml`

**Trigger:** `git push origin v1.0.0` (cualquier tag que empiece con `v`).

**Qué hace:**
1. Decodefica el keystore de release (almacenado como secret en base64).
2. Compila el APK de release con firma.
3. Crea un GitHub Release con el APK adjunto y release notes automáticas.

**Pasos para publicar una versión:**
```bash
git tag v1.0.0
git push origin v1.0.0
# → El workflow se dispara y crea el Release automáticamente
```

**Aprende con esto:** secrets, firma de APK, tags de Git como trigger, GitHub Releases.

---

## 4. Caché y velocidad

### Caché de Gradle

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: 17
    cache: gradle       # ← esto
```

`cache: gradle` cachea `~/.gradle/caches` y `~/.gradle/wrapper` entre runs.  
**Impacto:** la primera ejecución descarga todas las dependencias (~500 MB); las siguientes
las reutilizan → ahorra 2–4 minutos por run.

**Clave de caché:** se invalida automáticamente cuando cambia `gradle/wrapper/gradle-wrapper.properties`
o los archivos `.gradle.kts`.

### Daemon de Gradle en CI

```bash
./gradlew --no-daemon ...
```

En local el daemon de Gradle mantiene la JVM caliente entre builds → más rápido en local.
En CI cada job empieza limpio; el daemon no beneficia y consume RAM extra → se desactiva.

### Caché de AVD (emulador)

```yaml
- uses: actions/cache@v4
  with:
    path: ~/.android/avd/*
    key: avd-api26-${{ runner.os }}
```

El snapshot del emulador tarda ~3 min en crear. Al cachearlo por (api-level, OS), se reutiliza
en runs siguientes. La key incluye `runner.os` para invalidar si GitHub cambia el SO del runner.

---

## 5. Secrets y seguridad

### ¿Qué son los secrets?

Valores sensibles (contraseñas, keystores, tokens) que GitHub encripta en reposo y solo inyecta
en el runner durante la ejecución del workflow. **Nunca** aparecen en los logs (GitHub los
reemplaza con `***`).

### Cómo configurarlos en este repo

1. Abre el repo en GitHub → **Settings** → **Secrets and variables** → **Actions**
2. Clic en **New repository secret**
3. Añade los 4 secrets necesarios para el workflow de release:

| Secret | Qué es |
|--------|--------|
| `KEYSTORE_BASE64` | El archivo `release.jks` codificado en base64 |
| `KEYSTORE_PASSWORD` | Contraseña del keystore (la del `keytool -genkeypair`) |
| `KEY_ALIAS` | Alias de la clave (ej: `jobalert`) |
| `KEY_PASSWORD` | Contraseña de la clave (puede coincidir con KEYSTORE_PASSWORD) |

### Generar el keystore (una sola vez)

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -alias jobalert
```

Luego conviértelo a base64 para el secret:
```powershell
# PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```
```bash
# Linux/Mac
base64 -w 0 release.jks
```

**⚠ Guarda el `release.jks` y sus contraseñas en un gestor de contraseñas.** Sin ellas no
podrás firmar futuras actualizaciones de la app (Google Play requiere la misma firma).

### Validación del Gradle wrapper

```yaml
- uses: gradle/actions/wrapper-validation@v4
```

`gradlew` (y su JAR `gradle-wrapper.jar`) están commiteados en el repo. Un atacante con acceso
de escritura podría reemplazar el JAR con código malicioso que se ejecutaría en tu CI.
Esta Action verifica el hash SHA-256 del JAR contra la lista oficial de Gradle.

---

## 6. Cómo extender el pipeline

### Badge de estado en el README

Añade esto al inicio de `README.md` para ver el estado del CI de un vistazo:

```markdown
[![CI](https://github.com/jinraynor1/jobalert/actions/workflows/ci.yml/badge.svg)](https://github.com/jinraynor1/jobalert/actions/workflows/ci.yml)
```

### Branch protection rules

Impide mergear PRs con el build roto:
1. GitHub → Settings → Branches → Add branch protection rule
2. Branch name pattern: `master`
3. Activa: **Require status checks to pass before merging**
4. Selecciona el check `build-and-test` (de ci.yml)

### Matrices: múltiples API levels

Cuando quieras testear en varios niveles de Android a la vez:
```yaml
strategy:
  matrix:
    api-level: [26, 30, 34]
steps:
  - uses: reactivecircus/android-emulator-runner@v2
    with:
      api-level: ${{ matrix.api-level }}
```
Esto crea 3 jobs en paralelo, uno por API level — detectas regresiones en versiones antiguas
y nuevas simultáneamente.

### Análisis estático adicional

Puedes añadir en el job `build-and-test` de `ci.yml`:
```yaml
- name: Detekt (análisis estático Kotlin)
  run: ./gradlew --no-daemon detekt
```
Primero añade Detekt al `build.gradle.kts`:
```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}
```

### `workflow_dispatch`: trigger manual

```yaml
on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]
  workflow_dispatch:   # ← añade esto
```
Permite disparar el workflow manualmente desde la pestaña Actions de GitHub sin necesitar
un push. Útil para re-correr validaciones o forzar un build.

---

## 7. Troubleshooting

### El job de CI falla por lint

```
> Task :app:lintReportDebug FAILED
Lint found errors in the project; aborting build.
```

**Causa:** `abortOnError = true` es el default de lint Android. Si hay errores (no solo warnings),
el build falla.

**Solución 1 — Crear un baseline** (recomendada para empezar):
```powershell
$env:JAVA_HOME="D:\development\android\jbr"
.\gradlew :app:updateLintBaseline
# Commitear app/lint-baseline.xml
```
El baseline registra los errores actuales para que lint solo falle ante *nuevos* errores.

**Solución 2 — Configurar lint** (más control):
```kotlin
// en app/build.gradle.kts
android {
    lint {
        abortOnError = false   // no rompe el build
        htmlReport = true
        xmlReport = true
    }
}
```

### El emulador tarda mucho o crashea

- **KVM no habilitado:** el paso de habilitación de KVM debe correr antes del emulador.
- **Sin caché de AVD:** la primera ejecución siempre tarda ~5 min. Las siguientes deberían
  ser más rápidas con el caché.
- **API level muy alto:** API 34 tarda más en arrancar que API 26.

### El APK de release no está firmado

```
app-release-unsigned.apk en lugar de app-release.apk
```

**Causa:** la variable de entorno `KEYSTORE_FILE` está vacía — los secrets no están configurados
o el nombre del secret en GitHub no coincide con el que usa el workflow.

**Verificar:** Settings → Secrets and variables → Actions → confirma que los 4 secrets existen
y tienen exactamente los nombres `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

### Build más lento de lo esperado en CI

- La primera ejecución sin caché de Gradle descargará ~500 MB de dependencias: normal.
- Verifica que el step `setup-java` tenga `cache: gradle`.
- Usa `--no-daemon` para evitar que Gradle espere por un daemon que nunca llega.
