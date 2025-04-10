// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Раскомментируй, если будешь использовать Kapt (например, для Dagger, Room)
     kotlin("kapt")
    kotlin("plugin.serialization") version "1.9.23"
}

android {
    namespace = "by.toxic.phonecontacts" // Имя твоего пакета
    compileSdk = 34 // Используй актуальный SDK

    defaultConfig {
        applicationId = "by.toxic.phonecontacts"
        minSdk = 24 // Минимальная версия Android (Android 7.0)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true // Включаем View Binding
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0") // Актуальные версии
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2") // Для списка в конфигурации

    // Glide для загрузки изображений контактов (опционально, но удобно)
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    // annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0") // Устарело, Glide использует KSP или KAPT
    kapt ("com.github.bumptech.glide:compiler:4.16.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}