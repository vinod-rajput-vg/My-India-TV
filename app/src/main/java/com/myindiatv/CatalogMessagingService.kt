package com.myindiatv

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONArray

internal class CatalogMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        android.util.Log.i("CatalogFCM", "Received FCM data=${message.data}")
        if (message.data["type"] != "catalog_update") {
            android.util.Log.w("CatalogFCM", "Ignoring unexpected message type")
            return
        }

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
        // Full reload avoids stale screens if sender paths differ from catalog URL paths.
        android.util.Log.i("CatalogFCM", "Dispatch full refresh; paths=$changedPaths")
        CatalogPushBridge.dispatch(
            applicationContext,
            fullRefresh = true,
            changedPaths = changedPaths
        )
    }

    override fun onNewToken(token: String) {
        android.util.Log.i("CatalogFCM", "FCM token refreshed; subscribing to catalog-updates")
        FirebaseMessaging.getInstance()
            .subscribeToTopic("catalog-updates")
            .addOnFailureListener { error ->
                android.util.Log.e("CatalogFCM", "Topic subscription failed", error)
            }
    }
}
