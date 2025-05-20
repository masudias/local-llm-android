import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("de.undercouch.download")
}

android {
    namespace = "ai.altri.jam"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.altri.jam"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["appAuthRedirectScheme"] = ""

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Define the BuildConfig fields
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
        }
        val hfAccessToken = properties.getProperty("HF_ACCESS_TOKEN", "")
        buildConfigField("String", "HF_ACCESS_TOKEN", "\"$hfAccessToken\"")

        // Add LOCAL_MODEL configuration option
        val useLocalModel = properties.getProperty("USE_LOCAL_MODEL", "false").toBoolean()
        val bundledModelAssetName = properties.getProperty("BUNDLED_MODEL_ASSET_NAME", "")
        buildConfigField("Boolean", "USE_LOCAL_MODEL", useLocalModel.toString())
        buildConfigField("String", "BUNDLED_MODEL_ASSET_NAME", "\"$bundledModelAssetName\"")

        // Set manifest placeholder for internet permission based on USE_LOCAL_MODEL
        manifestPlaceholders["internetPermission"] = if (!useLocalModel) {
            "<uses-permission android:name=\"android.permission.INTERNET\" />"
        } else {
            "<!-- Internet permission not needed for local model -->"
        }
    }

    buildFeatures {
        buildConfig = true
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.9"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Don't compress task model files, they're already compressed
        jniLibs {
            useLegacyPackaging = true
        }
        dex {
            useLegacyPackaging = true
        }
    }

    // Don't compress the bundled model file(s) in assets - they're already compressed
    android.aaptOptions.noCompress += "task"
    android.aaptOptions.noCompress += "tflite"
}

// import DownloadModels task
project.extensions.extraProperties.set("ASSET_DIR", projectDir.toString() + "/src/main/assets")
project.extensions.extraProperties.set(
    "TEST_ASSETS_DIR",
    projectDir.toString() + "/src/androidTest/assets"
)

// Download default models; if you wish to use your own models then
// place them in the "assets" directory and comment out this line.
apply(from = "download_models.gradle")

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.12.0")

    implementation("com.google.mediapipe:tasks-genai:0.10.22")
    implementation("com.google.mediapipe:tasks-text:0.10.14")

    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("net.openid:appauth:0.11.1") // Add AppAuth for OAuth support
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("com.itextpdf:itext7-core:8.0.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
