import java.util.Base64
import org.gradle.api.tasks.Copy

plugins {
    id("com.android.application")
}

val releaseKeystoreBase64 = System.getenv("ANDROID_KEYSTORE_BASE64")
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystoreBase64,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

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

    sourceSets {
        getByName("main") {
            res.srcDir(layout.buildDirectory.dir("generated/res/channelIcons").get().asFile)
        }
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                val keystoreFile = layout.buildDirectory.file("signing/release.keystore").get().asFile
                keystoreFile.parentFile.mkdirs()
                keystoreFile.writeBytes(Base64.getDecoder().decode(releaseKeystoreBase64))
                storeFile = keystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

tasks.register<Copy>("copyChannelIcons") {
    from(layout.projectDirectory.dir("src/main/res/ch-drawable-nodpi"))
    into(layout.buildDirectory.dir("generated/res/channelIcons/drawable-nodpi"))
}

tasks.named("preBuild") {
    dependsOn("copyChannelIcons")
}

kotlin { jvmToolchain(17) }
