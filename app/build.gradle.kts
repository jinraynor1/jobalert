plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace = "com.jobalert"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jobalert"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.jobalert"
    }

    // ── Firma de release ────────────────────────────────────────────────────
    // En CI (workflow release.yml) las variables de entorno KEYSTORE_* se
    // inyectan desde los secrets del repositorio.
    // En local (debug) estas variables no existen → el bloque se omite y el
    // build de debug sigue funcionando sin keystore de release.
    //
    // Para firmar en local: exporta las variables antes de correr gradle:
    //   $env:KEYSTORE_FILE="ruta/release.jks"
    //   $env:KEYSTORE_PASSWORD="..."
    //   $env:KEY_ALIAS="jobalert"
    //   $env:KEY_PASSWORD="..."
    val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Solo aplica signingConfig si el keystore fue provisto (en CI).
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    packaging {
        resources.excludes += setOf(
            "META-INF/NOTICE.md",
            "META-INF/LICENSE.md",
            "META-INF/NOTICE",
            "META-INF/LICENSE"
        )
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
    kapt(libs.room.compiler)

    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.coroutines.android)
    implementation(libs.gson)
    implementation(libs.work.runtime.ktx)
    implementation(libs.security.crypto)
    implementation(libs.mail.android)
    implementation(libs.mail.activation)
    implementation(libs.appauth)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
}
