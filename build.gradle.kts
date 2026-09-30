android {
    namespace = "com.example.myindiatv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myindiatv"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("GITHUB_WORKSPACE")
                ?.let { file("$it/release.keystore") }

            if (keystorePath != null && keystorePath.exists()) {
                storeFile = keystorePath
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }

        debug {
            isMinifyEnabled = false
        }
    }
}
