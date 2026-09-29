plugins {
    id("com.android.application")
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
        release { isMinifyEnabled = false }
    }
}

kotlin { jvmToolchain(17) }
