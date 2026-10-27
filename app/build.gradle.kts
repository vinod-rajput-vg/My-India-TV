plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.myindiatv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.myindiatv"
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

dependencies {
    implementation("com.google.firebase:firebase-messaging:24.1.2")
}
