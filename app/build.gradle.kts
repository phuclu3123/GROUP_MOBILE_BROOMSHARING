plugins {
    alias(libs.plugins.android.application)
}

// Keep UI builds available without a Firebase config; apply the plugin once.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Local credentials are inputs to the build, never committed resources.
val mapEnvironment = providers.fileContents(rootProject.layout.projectDirectory.file(".env"))
    .asText.orElse("").get().lineSequence().filter { !it.trim().startsWith("#") && it.contains('=') }
    .associate { val parts = it.removePrefix("export ").split('=', limit = 2)
        parts[0].trim() to parts[1].trim().removeSurrounding("\"").removeSurrounding("'") }
fun mapKey(name: String): String {
    val value = providers.environmentVariable(name).orElse(mapEnvironment[name] ?: "").get()
    require(value.matches(Regex("[A-Za-z0-9._+=/\\-]*"))) { "Invalid format for $name" }
    return "\"$value\""
}

android {
    namespace = "vn.edu.ueh.phuclu.broomsharing"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "vn.edu.ueh.phuclu.broomsharing"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FIRESTORE_EMULATOR_HOST", "\"\"")
        buildConfigField("String", "MAPTILER_API_KEY", mapKey("MapTiler_API_KEY"))
        buildConfigField("String", "HEIGIT_API_KEY", mapKey("HeiGIT_API_KEY"))
    }

    buildTypes {
        debug {
            val emulatorHost = providers.gradleProperty("firestoreEmulatorHost").orElse("").get()
            require(emulatorHost in setOf("", "10.0.2.2", "127.0.0.1", "localhost"))
            buildConfigField("String", "FIRESTORE_EMULATOR_HOST", "\"$emulatorHost\"")
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.glide)
    implementation("org.maplibre.gl:android-sdk:13.7.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
