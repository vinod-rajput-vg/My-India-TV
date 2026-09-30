
    override fun onKeyDown(k: Int, e: KeyEvent): Boolean = if (handleDpadKey(k)) true else super.onKeyDown(k, e)
    private fun dp(v: Float) = v * resources.displayMetrics.density
}

private class ChannelView(context: Context, categoryIndex: Int) : View(context) {
    private val channels: List<Channel> = when (categoryIndex) {
        0 -> listOf(
            Channel("Star Gold 2 HD", "http://202.70.146.135:8000/play/a04q/index.m3u8", iconResId = iconId("star_gold_2_hd")),
            Channel("Zee Cinema", "https://d1g8wgjurz8via.cloudfront.net/bpk-tv/NGCHD/default/NGCHD.m3u8", iconResId = iconId("zeecinema")),
            Channel("Zee Cine Classic", "https://amg00862-amg00862c8-amgplt0173.playout.now3.amagi.tv/playlist/amg00862-amg00862c8-amgplt0173/playlist.m3u8", iconResId = iconId("zeecineclassic")),
            Channel("Zee Horror Nights", "https://amg00862-amg00862c7-amgplt0173.playout.now3.amagi.tv/playlist/amg00862-amg00862c7-amgplt0173/playlist.m3u8", iconResId = iconId("zeehorrornights")),
            Channel("Colors Cineplex Bollywood", "http://202.70.146.135:8000/play/a058/index.m3u8", iconResId = iconId("colorscineplexbollywood")),
            Channel("Shemaroo Bollywood", "https://prod-runn.cdn.runn.tv/shemaroo/stream/smrbol/playlist.m3u8", iconResId = iconId("shemaroo_bollywood")),
            Channel("B4U Kadak", "https://streams.tangotv.in/B4UKADAK/ORIGIN/index.m3u8", iconResId = iconId("b4u_kadak")),
            Channel("B4U Movies", "https://streams.tangotv.in/B4UMOVIES/ORIGIN/index.m3u8", iconResId = iconId("b4u_movies")),
            Channel("Goldmines", "https://streams.tangotv.in/GOLDMINES/ORIGIN/index.m3u8", iconResId = iconId("goldmines")),
            Channel("Goldmines 2", "https://mumt03.tangotv.in/Dsly5z3HGOLDMINES2/index.m3u8", iconResId = iconId("goldmines_2")),
            Channel("Goldmines Bollywood", "https://mumt03.tangotv.in/Dsly5z3HGOLDMINESBOLLYWOOD/index.m3u8", iconResId = iconId("goldmines_bollywood"))
        )
        1 -> listOf(
            Channel("Discovery HD Hindi", "http://202.70.146.135:8000/play/a05z/index.m3u8", iconResId = iconId("discoveryhdhindi")),
            Channel("Sony BBC Earth", "http://202.70.146.135:8000/play/a067/index.m3u8", iconResId = iconId("sonybbcearth")),
            Channel("Animal Planet Hindi", "http://27.116.22.53:5001/live/566.m3u8", iconResId = iconId("animal_planet_hindi")),