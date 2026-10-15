package com.myindiatv

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CatalogMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        subscribeToCatalogTopic()
    }

    override fun onCreate() {
        super.onCreate()
        subscribeToCatalogTopic()
    }

    private fun subscribeToCatalogTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic(CATALOG_TOPIC)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val paths = (message.data["paths"] ?: message.data["path"].orEmpty())
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (paths.isEmpty()) return

        sendBroadcast(Intent(ACTION_CATALOG_CHANGED).apply {
            setPackage(packageName)
            putStringArrayListExtra(EXTRA_CHANGED_PATHS, ArrayList(paths))
        })
    }

    companion object {
        const val ACTION_CATALOG_CHANGED = "com.myindiatv.ACTION_CATALOG_CHANGED"
        const val EXTRA_CHANGED_PATHS = "changed_paths"
        const val CATALOG_TOPIC = "catalog_updates"
    }
}
