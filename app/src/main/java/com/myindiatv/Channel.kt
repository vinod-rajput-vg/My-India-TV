package com.myindiatv

data class Channel(
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val iconResId: Int = 0
)
