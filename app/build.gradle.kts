import java.net.HttpURLConnection
import java.net.URL

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

    buildTypes {
        release { isMinifyEnabled = false }
    }
}

kotlin { jvmToolchain(17) }

val downloadCategoryIcons = tasks.register("downloadCategoryIcons") {
    val outputDir = file("src/main/res/drawable-nodpi")

    doLast {
        outputDir.mkdirs()

        // The icons are stored at this exact commit. Use the GitHub Contents
        // API with the raw media type instead of raw.githubusercontent.com.
        val apiBaseUrl = "https://api.github.com/repos/vinod-rajput-vg/My-Live-TV-M3U-Manager/contents/Icons"
        val ref = "7b85e8897b67e42a3d4349a955c0096a837d4522"
        val icons = mapOf(
            "entertainment.png" to "Entertainment.png",
            "imfotainment.png" to "Imfotainment.png",
            "news.png" to "News.png",
            "music.png" to "Music.png",
            "kids.png" to "Kids.png"
        )

        icons.forEach { (fileName, sourceName) ->
            val urlString = "$apiBaseUrl/$sourceName?ref=$ref"
            val destination = File(outputDir, fileName)
            var lastError: Exception? = null

            repeat(3) { attempt ->
                try {
                    val connection = URL(urlString).openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 20000
                        connection.readTimeout = 20000
                        connection.instanceFollowRedirects = true
                        connection.doInput = true
                        connection.useCaches = false
                        connection.setRequestProperty("User-Agent", "My-India-TV Android Build")
                        connection.setRequestProperty("Accept", "application/vnd.github.raw")

                        connection.connect()
                        check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                            "HTTP ${connection.responseCode} while downloading $urlString"
                        }

                        val bytes = connection.inputStream.use { it.readBytes() }
                        check(
                            bytes.size >= 8 &&
                                bytes[0] == 0x89.toByte() &&
                                bytes[1] == 0x50.toByte() &&
                                bytes[2] == 0x4E.toByte() &&
                                bytes[3] == 0x47.toByte()
                        ) {
                            "Downloaded file is not a valid PNG: $urlString"
                        }

                        destination.writeBytes(bytes)
                        lastError = null
                    } finally {
                        connection.disconnect()
                    }

                    return@repeat
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < 2) Thread.sleep(1000L)
                }
            }

            check(destination.exists() && destination.length() > 0L) {
                "Failed to download category icon '$fileName' from $urlString: ${lastError?.message}"
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadCategoryIcons)
}
