plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    signingConfigs {
        getByName("debug") {
            storeFile =
                file("C:\\Users\\kitti\\AndroidStudioProjects\\Whoareyou\\.keystore\\WhoAreYou_App.jks")
            storePassword = "Kittipob262546"
            keyAlias = "AppWhoAreYou"
            keyPassword = "Kittipob262546"
        }

        create("release") {  // ✅ เพิ่ม release signingConfig
            storeFile = file("C:/Users/kitti/.keystore/whoareyou.jks") // ✅ ใช้ Keystore เดียวกัน
            storePassword = "Kittipob262546"
            keyAlias = "AppWhoAreYou"
            keyPassword = "Kittipob262546"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release") // ✅ ใช้ release signingConfig
        }
    }
    namespace = "com.kittipob.whoareyou"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kittipob.whoareyou"
        minSdk = 27
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    bundle {
        storeArchive {
            enable = true
        }
    }

    buildFeatures {
        viewBinding = true
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
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("com.squareup.okhttp3:okhttp:4.9.1")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("io.coil-kt:coil:2.6.0")
}