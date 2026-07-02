plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Nonaktifkan baseline profile
    // alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.tarang.launcher"
    compileSdk = 29

    defaultConfig {
        applicationId = "com.tarang.launcher"
        minSdk = 21
        targetSdk = 28
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    // Hapus profileinstaller
    // implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hapus baselineProfile
    // "baselineProfile"(project(":baselineprofile"))

    // Tambahkan coreLibraryDesugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
