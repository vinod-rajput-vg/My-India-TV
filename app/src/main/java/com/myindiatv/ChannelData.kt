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
        .useLines { sequence ->
            sequence.map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }

    return lines.chunked(3).mapNotNull { block ->
        if (block.size != 3) return@mapNotNull null

        val name = block[0].removeSurrounding("\"")
        val streamUrl = block[1].removeSurrounding("\"")
        val iconFile = block[2].removeSurrounding("\"")
        val iconName = iconFile
            .substringAfterLast("/")
            .substringBeforeLast(".")
            .lowercase()

        // Resolve the PNG dynamically from the drawable resource name.
        // No channel/icon entry needs to be added to Kotlin when a new PNG is added.
        val iconResId = context.resources.getIdentifier(
            iconName,
            "drawable",
            context.packageName
        )

        if (iconResId == 0) return@mapNotNull null

        Channel(name, streamUrl, iconResId)
    }
}
