/*
 * 아로새김(Arosaegim) — :app 모듈 빌드 스크립트.
 * 툴체인: AGP 8.8.2 / Kotlin 2.0.0 / JDK 17 / compileSdk·targetSdk 35 / minSdk 28
 */

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 릴리스 서명 값은 저장소 밖의 keystore.properties(gitignore)에서 읽는다. 키 파일 자체는 커밋 금지(00문서 2-1 #5).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.arosaegim"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.arosaegim"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // keystore.properties 가 있을 때만 실서명. 없으면 디버그 서명으로 로컬 검증용 release 생성.
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // --- Pleos Playground SDK (Vehicle / 네비 / 융합 위치 / TTS) ---
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.pleos.tts)

    // --- Android Automotive android.car API ---
    // 에뮬레이터 시스템 이미지에 런타임 존재. 컴파일용 jar는 통합 단계에서
    // app/libs/android.car.jar 로 배치된다(compileOnly 이므로 APK에 미포함).
    compileOnly(files("libs/android.car.jar"))

    // --- Jetpack Compose (BOM으로 버전 통일) ---
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.core.ktx)
    debugImplementation(libs.compose.ui.tooling)

    // --- 직렬화 (코스/장소 데이터 JSON) ---
    implementation(libs.kotlinx.serialization.json)

    // --- 테스트 ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
