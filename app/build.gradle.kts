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
        versionName = "1.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
            install("numpy==1.19.5")
            install("numba==0.48.0")
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

    implementation(libs.ffmpeg.kit.audio)
    implementation(libs.okhttp)
    implementation(libs.smile.core)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
