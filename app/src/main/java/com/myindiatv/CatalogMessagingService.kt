package com.myindiatv

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService

/**
 * Receives data-only catalog refresh events. The push contains a signal/version only;
 * the app always downloads the canonical catalog from GitHub.
 */
class CatalogMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Firebase manages token lifecycle. Re-subscribe after token rotation.
        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic(CATALOG_TOPIC)
        }
    }

    override fun onMessageReceived(message: com.google.firebase.messaging.RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data["event"] != CATALOG_UPDATED_EVENT) return

        CatalogRefreshCoordinator.markPending(applicationContext)
    }

    companion object {
        const val CATALOG_TOPIC = "my-india-tv-catalog"
        const val CATALOG_UPDATED_EVENT = "catalog_updated"
    }
}

/**
 * Keeps a pending refresh marker so pushes received while the Activity is stopped are
 * handled when it resumes. Also notifies the foreground Activity for prompt refresh.
 */
internal object CatalogRefreshCoordinator {
    private const val PREFS_NAME = "catalog_sync"
    private const val KEY_PENDING = "catalog_refresh_pending"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var listener: (() -> Unit)? = null

    fun setListener(callback: () -> Unit) {
        listener = callback
    }

    fun clearListener() {
        listener = null
    }

    fun markPending(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .apply()

        mainHandler.post { listener?.invoke() }
    }

    fun consumePending(context: Context): Boolean {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!preferences.getBoolean(KEY_PENDING, false)) return false
        preferences.edit().putBoolean(KEY_PENDING, false).apply()
        return true
    }
}
