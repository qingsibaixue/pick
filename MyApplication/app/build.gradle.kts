plugins {
    id("com.android.application")
    // 如果你不使用 Kotlin，可以不加 kotlin-android 插件；否则保留
    // id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.goldmod"
    compileSdk = 34   // 使用稳定的 API 34，而不是 36

    defaultConfig {
        applicationId = "com.example.goldmod"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false   // 注意是 isMinifyEnabled，不是 optimization { enable = false }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // compileOnly("de.robv.android.xposed:api:82")
    compileOnly(files("libs/api-82.jar"))
}