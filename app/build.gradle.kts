plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Подписывание релиза через переменные окружения (CI) вместо хранения keystore в репозитории.
// Локально: задайте те же переменные в оболочке (см. CI_SETUP.md).
val keystoreFileEnv = System.getenv("KEYSTORE_FILE")
val keystorePasswordEnv = System.getenv("KEYSTORE_PASSWORD") ?: ""
val keyAliasEnv = System.getenv("KEY_ALIAS") ?: ""
val keyPasswordEnv = System.getenv("KEY_PASSWORD") ?: ""

// Версия может переопределяться из CI (номер тега и номер запуска workflow).
val versionNameOverride = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
val versionCodeOverride = System.getenv("VERSION_CODE")?.toIntOrNull()

android {
    namespace = "ru.fitapp"
    compileSdk = 36
    // Локально установлены только 36.0.0 и 37.0.0; дефолтную 35.0.0 SDK не скачает офлайн.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "ru.fitapp"
        minSdk = 26
        targetSdk = 36
        // 7 — следующий номер после последней CI-сборки: локальная сборка должна
        // ставиться поверх неё, иначе установщик видит понижение версии.
        versionCode = versionCodeOverride ?: 7
        versionName = versionNameOverride ?: "0.2.2"
    }

    signingConfigs {
        create("release") {
            if (keystoreFileEnv != null) {
                val ksFile = file(keystoreFileEnv)
                if (!ksFile.exists()) {
                    throw GradleException(
                        "KEYSTORE_FILE указывает на несуществующий файл: ${ksFile.absolutePath}"
                    )
                }
                storeFile = ksFile
                storePassword = keystorePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreFileEnv != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        // AiLog/SystemClock — android.util.Log в JVM-тестах не реализован;
        // заглушки возвращают дефолты вместо исключения.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    // DataStore + secure prefs
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha07")

    // CameraX
    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Network + JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Images
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
