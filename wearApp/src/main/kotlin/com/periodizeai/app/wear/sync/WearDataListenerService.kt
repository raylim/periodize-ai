package com.periodizeai.app.wear.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.periodizeai.app.sync.WEAR_DATA_PATH
import com.periodizeai.app.sync.WEAR_PAYLOAD_KEY
import com.periodizeai.app.sync.WatchWorkoutPayload
import com.periodizeai.app.wear.data.WearDataStore
import kotlinx.serialization.json.Json

class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WEAR_DATA_PATH
            ) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = dataMap.getString(WEAR_PAYLOAD_KEY) ?: return@forEach
                val payload = if (json.isEmpty()) null else try {
                    Json.decodeFromString(WatchWorkoutPayload.serializer(), json)
                } catch (_: Exception) { null }
                WearDataStore.update(this, payload)
            }
        }
    }
}
