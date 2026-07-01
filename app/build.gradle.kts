plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Nonaktifkan sementara jika baseline profile bermasalah
    // alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.tarang.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tarang.launcher"
        minSdk = 23
        targetSdk = 35
        versionCode = 7
        versionName = "0.2.4"

        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Jika baseline profile diaktifkan, tambahkan:
            // enableProfileInstaller = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Aktifkan desugaring untuk API < 24
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.datastore.preferences)

    // Jika baseline profile tidak dipakai, komentari atau hapus:
    // implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Jika baseline profile dinonaktifkan, komentari juga:
    // "baselineProfile"(project(":baselineprofile"))

    // ✅ Tambahkan dependency untuk coreLibraryDesugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
