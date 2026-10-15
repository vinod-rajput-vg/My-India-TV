plugins {
    id("com.android.application")
}

android {
    namespace = "com.myindiatv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.myindiatv"
        // Firebase Firestore 27.x requires Android API 24 or newer.
        minSdk = 24
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
