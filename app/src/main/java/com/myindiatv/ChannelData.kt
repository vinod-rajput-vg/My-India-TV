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
        val iconName = iconFile.removeSuffix(".png")
        val iconResId = iconResource(iconName)

        if (iconResId == 0) return@mapNotNull null

        Channel(name, streamUrl, iconResId)
    }
}

private fun iconResource(name: String): Int = when (name) {
    "star_gold" -> R.drawable.star_gold
    "star_gold_2_hd" -> R.drawable.star_gold_2_hd
    "star_gold_select_hd" -> R.drawable.star_gold_select_hd
    "sony_max_hd" -> R.drawable.sony_max_hd
    "sony_max_2" -> R.drawable.sony_max_2
    "sony_pix_hd" -> R.drawable.sony_pix_hd
    "and_pictures_hd" -> R.drawable.and_pictures_hd
    "zee_cinema_hd" -> R.drawable.zee_cinema_hd
    "zee_action" -> R.drawable.zee_action
    "zee_bollywood" -> R.drawable.zee_bollywood
    "zeeclassic" -> R.drawable.zeeclassic
    "zeecineclassic" -> R.drawable.zeecineclassic
    "xplor_hd" -> R.drawable.xplor_hd
    "star_utsav_movies" -> R.drawable.star_utsav_movies
    "sony_wah" -> R.drawable.sony_wah
    "colors_cineplex" -> R.drawable.colors_cineplex
    "colors_cineplex_hd" -> R.drawable.colors_cineplex_hd
    "colorscineplexbollywood" -> R.drawable.colorscineplexbollywood
    "colors_cineplex_superhits" -> R.drawable.colors_cineplex_superhits
    "shemaroo_bollywood" -> R.drawable.shemaroo_bollywood
    "b4u_kadak" -> R.drawable.b4u_kadak
    "b4u_movies" -> R.drawable.b4u_movies
    "goldmines" -> R.drawable.goldmines
    "goldmines_2" -> R.drawable.goldmines_2
    "goldmines_bollywood" -> R.drawable.goldmines_bollywood
    "discoveryhdhindi" -> R.drawable.discoveryhdhindi
    "sony_bbc_earth_hd" -> R.drawable.sony_bbc_earth_hd
    "tlc_hd" -> R.drawable.tlc_hd
    "animal_planet_hd" -> R.drawable.animal_planet_hd
    "nat_geo_wild_hd" -> R.drawable.nat_geo_wild_hd
    "national_geographic_hd" -> R.drawable.national_geographic_hd
    "discovery_science_hindi" -> R.drawable.discovery_science_hindi
    "history_tv18_hd" -> R.drawable.history_tv18_hd
    "tv9_bharatvarsh" -> R.drawable.tv9_bharatvarsh
    "tv_9_gujarat" -> R.drawable.tv_9_gujarat
    "tv9_maharashtra" -> R.drawable.tv9_maharashtra
    "zee_24_kalak" -> R.drawable.zee_24_kalak
    "republic_bharat" -> R.drawable.republic_bharat
    "news_nation" -> R.drawable.news_nation
    "aaj_tak" -> R.drawable.aaj_tak
    "abp_news_india" -> R.drawable.abp_news_india
    "times_now_navbharat" -> R.drawable.times_now_navbharat
    "india_tv" -> R.drawable.india_tv
    "zee_24_taas" -> R.drawable.zee_24_taas
    "b4u_music" -> R.drawable.b4u_music
    "shemaroo_filmy_gaane" -> R.drawable.shemaroo_filmy_gaane
    "nick_hindi" -> R.drawable.nick_hindi
    "pogo_hindi" -> R.drawable.pogo_hindi
    "sonic_hindi" -> R.drawable.sonic_hindi
    "cartoon_network_hindi" -> R.drawable.cartoon_network_hindi
    "cartoon_network_hd_plus_hindi" -> R.drawable.cartoon_network_hd_plus_hindi
    "discovery_kids_hindi" -> R.drawable.discovery_kids_hindi
    "hungama" -> R.drawable.hungama
    "superhungama" -> R.drawable.superhungama
    "disney_channel" -> R.drawable.disney_channel
    "zee_tv" -> R.drawable.zee_tv
    "star_plus_hd" -> R.drawable.star_plus_hd
    "sony_sab_hd" -> R.drawable.sony_sab_hd
    "colors" -> R.drawable.colors
    "colors_gujarati" -> R.drawable.colors_gujarati
    "star_utsav" -> R.drawable.star_utsav
    "zing" -> R.drawable.zing
    "and_tv_hd" -> R.drawable.and_tv_hd
    "sony_entertainment_television_hd" -> R.drawable.sony_entertainment_television_hd
    "star_sports_1_hd" -> R.drawable.star_sports_1_hd
    "sony_ten_3_hd" -> R.drawable.sony_ten_3_hd
    "underworld_rise_of_the_lycans_hindi" -> R.drawable.underworld_rise_of_the_lycans_hindi
    else -> 0
}
