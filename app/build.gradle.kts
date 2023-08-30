plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.test"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.onboarding" //"com.example.test"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.0")

    implementation(project(":fingerPrintModule"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
//    implementation(files("./libs/FingerprintModule-debug.aar"))
//    implementation(files("./libs/opencv_411-release.aar"))
//    implementation(files("./libs/T5AirSnap-release.aar"))
//    implementation(files("./libs/touchview-release.aar"))

//    val cameraxVersion = "1.2.3"
//
//    implementation( "androidx.camera:camera-core:${cameraxVersion}")
//    implementation( "androidx.camera:camera-camera2:${cameraxVersion}")
//    implementation( "androidx.camera:camera-lifecycle:${cameraxVersion}")
//    implementation( "androidx.camera:camera-video:${cameraxVersion}")
//
//    implementation ("androidx.camera:camera-view:${cameraxVersion}")
//    implementation( "androidx.camera:camera-extensions:${cameraxVersion}")
}