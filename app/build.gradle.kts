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
        versionCode = 4
        versionName = "1.0.3"
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

        // Use the GitHub Contents API with the raw media type instead of
        // raw.githubusercontent.com. This avoids the HTTP 404 encountered
        // by GitHub Actions while fetching the binary PNG files.
        val apiBaseUrl = "https://api.github.com/repos/vinod-rajput-vg/My-Live-TV-M3U-Manager/contents/Icons"
        val icons = mapOf(
            "entertainment.png" to "Entertainment.png",
            "imfotainment.png" to "Imfotainment.png",
            "news.png" to "News.png",
            "music.png" to "Music.png",
            "kids.png" to "Kids.png"
        )

        icons.forEach { (fileName, sourceName) ->
            val urlString = "$apiBaseUrl/$sourceName?ref=main"
            val destination = File(outputDir, fileName)
            var lastError: Exception? = null

            repeat(3) { attempt ->
                try {
                    val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 20000
                        readTimeout = 20000
                        instanceFollowRedirects = true
                        doInput = true
                        useCaches = false
                        setRequestProperty("User-Agent", "My-India-TV Android Build")
                        setRequestProperty("Accept", "application/vnd.github.raw+json")
                    }

                    try {
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
