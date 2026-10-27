package com.myindiatv

import android.content.Context

internal object CatalogPushBridge {
    private const val PREFS = "catalog_push"
    private const val PENDING_FULL = "pending_full_refresh"
    private const val PENDING_PATHS = "pending_changed_paths"

    @Volatile private var listener: ((Boolean, Set<String>) -> Unit)? = null

    fun register(context: Context, callback: (Boolean, Set<String>) -> Unit) {
        val pending: Pair<Boolean, Set<String>>
        synchronized(this) {
            listener = callback
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            pending = prefs.getBoolean(PENDING_FULL, false) to
                prefs.getStringSet(PENDING_PATHS, emptySet()).orEmpty().toSet()
            prefs.edit().remove(PENDING_FULL).remove(PENDING_PATHS).apply()
        }
        if (pending.first || pending.second.isNotEmpty()) callback(pending.first, pending.second)
    }

    fun unregister() {
        synchronized(this) { listener = null }
    }

    fun dispatch(context: Context, fullRefresh: Boolean, changedPaths: Set<String>) {
        val callback: ((Boolean, Set<String>) -> Unit)?
        synchronized(this) {
            callback = listener
            if (callback == null) {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val previous = prefs.getStringSet(PENDING_PATHS, emptySet()).orEmpty()
                prefs.edit()
                    .putBoolean(PENDING_FULL, fullRefresh || prefs.getBoolean(PENDING_FULL, false))
                    .putStringSet(PENDING_PATHS, (previous + changedPaths).toSet())
                    .apply()
            }
        }
        callback?.invoke(fullRefresh, changedPaths)
    }
}
