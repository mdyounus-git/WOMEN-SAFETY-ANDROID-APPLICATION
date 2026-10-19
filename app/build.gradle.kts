import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Firebase is optional at build time: the app builds and runs without it, and the
// live-sharing features switch on once app/google-services.json is added.
// See README.md → "Set up Firebase".
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Secrets live in local.properties (never committed). See README.md.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String = (localProps.getProperty(name) ?: "").trim()

android {
    namespace = "com.example.womensafety"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.womensafety"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = secret("MAPS_API_KEY")
        // Optional TURN relay for live video on strict mobile networks (free tiers exist).
        buildConfigField("String", "TURN_URL", "\"${secret("TURN_URL")}\"")
        buildConfigField("String", "TURN_USERNAME", "\"${secret("TURN_USERNAME")}\"")
        buildConfigField("String", "TURN_PASSWORD", "\"${secret("TURN_PASSWORD")}\"")
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)

    // Free backend: Firebase Spark plan (Anonymous Auth + Realtime Database)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)

    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)

    // Live camera + microphone streaming (phone to phone)
    implementation(libs.stream.webrtc)

    // QR code generation and scanning
    implementation(libs.zxing.embedded)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
