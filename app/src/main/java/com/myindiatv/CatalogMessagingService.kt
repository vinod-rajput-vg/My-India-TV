package com.myindiatv

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONArray

internal class CatalogMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "catalog_update") return

        val changedPaths = try {
            val json = JSONArray(message.data["changedPaths"].orEmpty())
            buildSet {
                for (index in 0 until json.length()) {
                    json.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
        CatalogPushBridge.dispatch(
            applicationContext,
            fullRefresh = changedPaths.isEmpty(),
            changedPaths = changedPaths
        )
    }

    override fun onNewToken(token: String) {
        FirebaseMessaging.getInstance().subscribeToTopic("catalog-updates")
    }
}
