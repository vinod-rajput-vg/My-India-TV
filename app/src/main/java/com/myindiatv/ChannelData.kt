package com.myindiatv

data class Category(
    val name: String,
    val iconResId: Int,
    val fileName: String
)

val categories = listOf(
    Category("Entertainment", R.drawable.entertainment, "entertainment.txt"),
    Category("Infotainment", R.drawable.imfotainment, "infotainment.txt"),
    Category("News", R.drawable.news, "news.txt"),
    Category("Music", R.drawable.music, "music.txt"),
    Category("Kids", R.drawable.kids, "kids.txt"),
    Category("Drama", R.drawable.drama, "drama.txt"),
    Category("Sports", R.drawable.sports, "sports.txt"),
    Category("Movies", R.drawable.movies, "movies.txt")
)

fun loadChannels(context: android.content.Context, fileName: String): List<Channel> {
    val lines = context.assets.open("channels/$fileName")
        .bufferedReader()
        .use { it.readLines() }

    // Keep legacy M3U support while the channel files use Name / Icon URL / Stream URL.
    if (lines.any { it.trim().startsWith("#EXTINF:", ignoreCase = true) }) {
        val result = mutableListOf<Channel>()
        var name: String? = null
        var iconUrl = ""

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.equals("#EXTM3U", ignoreCase = true)) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val comma = line.indexOf(',')
                if (comma < 0) continue
                val attributes = line.substring(0, comma)
                name = line.substring(comma + 1).trim()
                iconUrl = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE)
                    .find(attributes)?.groupValues?.getOrNull(1).orEmpty()
            } else if (!line.startsWith("#") && !name.isNullOrBlank()) {
                result += Channel(name = name, streamUrl = line, iconUrl = iconUrl)
                name = null
                iconUrl = ""
            }
        }
        return result
    }

    // New plain-text format: each channel is exactly three non-empty lines.
    val entries = lines.map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

    return entries.chunked(3)
        .filter { it.size == 3 && it[0].isNotBlank() && it[2].isNotBlank() }
        .map { entry ->
            Channel(
                name = entry[0],
                iconUrl = entry[1],
                streamUrl = entry[2]
            )
        }
}
