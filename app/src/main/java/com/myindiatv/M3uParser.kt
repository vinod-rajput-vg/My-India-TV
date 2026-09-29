package com.myindiatv

object M3uParser {
    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var info: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", true)) { info = line; continue }
            if (line.startsWith("#")) continue
            val meta = info ?: continue
            val comma = meta.indexOf(',')
            val name = if (comma >= 0) meta.substring(comma + 1).trim() else "Unknown Channel"
            fun attr(key: String): String? {
                val marker = key + "=\""
                val start = meta.indexOf(marker, ignoreCase = true)
                if (start < 0) return null
                val valueStart = start + marker.length
                val end = meta.indexOf('"', valueStart)
                return if (end > valueStart) meta.substring(valueStart, end) else null
            }
            out += Channel(name, line, attr("tvg-logo"), attr("group-title"))
            info = null
        }
        return out
    }
}
