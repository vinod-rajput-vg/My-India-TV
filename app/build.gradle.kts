import org.gradle.api.tasks.Copy

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
        versionCode = 6
        versionName = "1.0.5"
    }

    sourceSets {
        getByName("main") {
            res.srcDir(layout.buildDirectory.dir("generated/res/channelIcons").get().asFile)
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
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
