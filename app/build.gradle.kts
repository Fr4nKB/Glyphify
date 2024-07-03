plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.chaquo.python")
}

android {
    namespace = "com.frank.glyphify"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.frank.glyphify"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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
        viewBinding = true
    }

}

chaquopy {
    defaultConfig {
        buildPython(System.getenv("PYTHON38_PATH"))
        version = "3.8"
        pip {
            install("numpy")
            install("numba")
            install("joblib==1.0.0")
            install("resampy==0.2.2")
            install("librosa==0.7.2")
        }
    }
    productFlavors { }
    sourceSets { }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.arthenica:ffmpeg-kit-full:6.0-2")
    implementation("com.github.paramsen:noise:2.0.0")
    implementation("com.github.wendykierp:JTransforms:3.1")
}
