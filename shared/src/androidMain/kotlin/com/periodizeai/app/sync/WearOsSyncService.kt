package com.periodizeai.app.sync

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object WearOsSyncService {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun syncWorkout(payload: WatchWorkoutPayload?) {
        val ctx = appContext ?: return
        withContext(Dispatchers.IO) {
            try {
                val request = PutDataMapRequest.create(WEAR_DATA_PATH).apply {
                    dataMap.putString(
                        WEAR_PAYLOAD_KEY,
                        if (payload != null) Json.encodeToString(WatchWorkoutPayload.serializer(), payload) else "",
                    )
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }
                Wearable.getDataClient(ctx).putDataItem(request.asPutDataRequest().setUrgent()).await()
            } catch (_: Exception) {
                // Wear not available — silently no-op
            }
        }
    }
}
