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
    Category("TataPlay", R.drawable.tataplay, "tataplay.txt"),
    Category("Movies", R.drawable.movies, "movies.txt")
)

fun loadChannels(context: android.content.Context, fileName: String): List<Channel> {
    return context.assets.open("channels/$fileName")
        .bufferedReader()
        .useLines { lines ->
            val result = mutableListOf<Channel>()
            var name: String? = null
            var streamUrl: String? = null
            var iconUrl: String? = null

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.equals("#EXTM3U", ignoreCase = true)) continue

                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val comma = line.indexOf(',')
                    if (comma < 0) continue

                    val attributes = line.substring(0, comma)
                    val displayName = line.substring(comma + 1).trim()
                    val logo = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE)
                        .find(attributes)
                        ?.groupValues
                        ?.getOrNull(1)
                        .orEmpty()

                    name = displayName
                    streamUrl = null
                    iconUrl = logo
                } else if (!line.startsWith("#")) {
                    streamUrl = line
                    if (!name.isNullOrBlank() && !streamUrl.isNullOrBlank()) {
                        result += Channel(
                            name = name,
                            streamUrl = streamUrl,
                            iconUrl = iconUrl.orEmpty()
                        )
                    }
                    name = null
                    streamUrl = null
                    iconUrl = null
                }
            }

            result
        }
}
