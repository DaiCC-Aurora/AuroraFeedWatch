plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

// 云端 Feed 地址：默认取 gradle.properties 的 FEED_BASE_URL，
// 也可在命令行用 -PFEED_BASE_URL=https://xxx.vercel.app/ 覆盖。
val feedBaseUrl: String = (project.findProperty("FEED_BASE_URL") as String?) ?: "https://your-app.vercel.app/"

android {
    namespace = "com.aurora.podcast"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aurora.podcast"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"

        buildConfigField("String", "BASE_URL", "\"${feedBaseUrl.trimEnd('/')}/\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ---- Jetpack Compose for Wear OS ----
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // ---- Media（MediaSessionCompat / MediaBrowserServiceCompat）----
    implementation("androidx.media:media:1.7.0")

    // ---- Media3 ExoPlayer ----
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")

    // ---- WorkManager ----
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // ---- Retrofit + OkHttp ----
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ---- Room ----
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ---- DataStore ----
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ---- Lifecycle ----
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")

    // ---- Coroutines ----
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
}