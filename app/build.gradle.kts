plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    signingConfigs {
        getByName("debug") {
            storeFile = file("$rootDir/.keystore/WhoAreYou_App.jks")
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

    // *** จุดที่ควรแก้ไข: คุณมี buildTypes block ซ้ำกัน ***
    // บล็อกนี้ควรถูกรวมเข้ากับบล็อก buildTypes ด้านล่าง
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

    // *** บล็อก buildTypes ที่สอง: ควรนำโค้ดจากบล็อกด้านบนมารวมที่นี่ ***
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // ✅ เพิ่ม signingConfig ตรงนี้
            signingConfig = signingConfigs.getByName("release")
        }
        // คุณอาจต้องการกำหนดค่า debug build type ด้วยเช่นกัน
        debug {
            // ตัวอย่าง:
            // isMinifyEnabled = false
            // signingConfig = signingConfigs.getByName("debug")
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


    // *** จุดที่ควรแก้ไข: คุณมี OkHttp ซ้ำซ้อนกันและเวอร์ชันต่างกัน ***
    implementation ("com.squareup.okhttp3:okhttp:4.9.1") // เวอร์ชันเก่า
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.1") // เวอร์ชันเก่า
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // เวอร์ชันใหม่กว่า
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")
    
    // *** Coil Dependency: เวอร์ชัน 2.6.0 โอเค แต่มี 2.7.0 ที่ใหม่กว่า (ณ ตอนนี้) ***
    implementation("io.coil-kt:coil:2.6.0") // สามารถอัปเดตเป็น 2.7.0 ได้ถ้าต้องการ

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}