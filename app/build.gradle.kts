plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Nonaktifkan baseline profile
    // alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.tarang.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tarang.launcher"
        minSdk = 23
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
    // Dependencies umum
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    
    // Compose untuk semua versi
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    
    // TV Material3 - hanya untuk modern
    modernImplementation(libs.androidx.tv.material)
    
    // Untuk STB, gunakan Leanback
    stbImplementation('androidx.leanback:leanback:1.0.0')
    stbImplementation('androidx.leanback:leanback-preference:1.0.0')
    
    // Hapus profileinstaller
    implementation(libs.androidx.profileinstaller) {
        exclude group: 'androidx.profileinstaller'
    }
    
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
