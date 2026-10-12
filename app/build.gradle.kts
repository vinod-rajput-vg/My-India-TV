plugins {
    id("com.android.application")
}

dependencies {
    implementation("com.google.firebase:firebase-firestore:25.0.0")
}

android {
    namespace = "com.myindiatv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.myindiatv"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            optimization {
                enable = true
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}
