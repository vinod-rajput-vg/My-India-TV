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
        versionCode = 3
        versionName = "1.0.2"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
}

kotlin { jvmToolchain(17) }

val downloadCategoryIcons by tasks.registering {
    val outputDir = file("src/main/res/drawable-nodpi")

    doLast {
        outputDir.mkdirs()

        val icons = mapOf(
            "entertainment.png" to "https://raw.githubusercontent.com/vinod-rajput-vg/My-Live-TV-M3U-Manager/main/Icons/Entertainment.png",
            "imfotainment.png" to "https://raw.githubusercontent.com/vinod-rajput-vg/My-Live-TV-M3U-Manager/main/Icons/Imfotainment.png",
            "news.png" to "https://raw.githubusercontent.com/vinod-rajput-vg/My-Live-TV-M3U-Manager/main/Icons/News.png",
            "music.png" to "https://raw.githubusercontent.com/vinod-rajput-vg/My-Live-TV-M3U-Manager/main/Icons/Music.png",
            "kids.png" to "https://raw.githubusercontent.com/vinod-rajput-vg/My-Live-TV-M3U-Manager/main/Icons/Kids.png"
        )

        icons.forEach { (fileName, urlString) ->
            val destination = File(outputDir, fileName)
            var lastError: Exception? = null

            repeat(3) { attempt ->
                if (lastError == null && destination.exists() && destination.length() > 0L) return@repeat

                try {
                    val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 20000
                        readTimeout = 20000
                        instanceFollowRedirects = true
                        doInput = true
                        useCaches = false
                        setRequestProperty("User-Agent", "My-India-TV Android Build")
                        setRequestProperty("Accept", "image/png,image/*,*/*;q=0.8")
                        setRequestProperty("Accept-Encoding", "identity")
                    }

                    try {
                        connection.connect()
                        check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                            "HTTP ${connection.responseCode} while downloading $urlString"
                        }

                        val bytes = connection.inputStream.use { it.readBytes() }
                        check(bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
                            "Downloaded file is not a valid PNG: $urlString"
                        }

                        destination.writeBytes(bytes)
                        lastError = null
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < 2) Thread.sleep(1000L)
                }
            }

            check(destination.exists() && destination.length() > 0L) {
                "Failed to download $urlString: ${lastError?.message}"
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadCategoryIcons)
}
