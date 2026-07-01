plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Sementara nonaktifkan baseline profile jika masih error
    // alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.tarang.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tarang.launcher"
        minSdk = 23                      // ✨ diubah dari 28 ke 23
        targetSdk = 35
        versionCode = 7
        versionName = "0.2.4"

        // Tambahkan multiDex (opsional tapi aman)
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
            // Nonaktifkan baseline profile jika menggunakan plugin
            // enableProfileInstaller = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Aktifkan desugaring untuk API < 24 (opsional)
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

    // Hapus atau komen sementara jika baseline profile bermasalah
    // implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Sementara nonaktifkan module baselineprofile
    // "baselineProfile"(project(":baselineprofile"))

    // Tambahkan coreLibraryDesugaring jika compileOptions sudah diaktifkan
    // coreLibraryDesugaring(libs.desugar.jdk.libs) // sesuaikan dengan version catalog
}
