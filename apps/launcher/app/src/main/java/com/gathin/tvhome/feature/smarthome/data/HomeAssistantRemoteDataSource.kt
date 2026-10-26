package com.gathin.tvhome.feature.smarthome.data

import com.gathin.tvhome.feature.smarthome.data.HassClient
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject


interface HomeAssistantRemoteDataSource {
    suspend fun getStates(): List<SmartHomeDevice>
    suspend fun getAreas(): Map<String, String>
    suspend fun callService(
        device: SmartHomeDevice,
        action: SmartHomeAction,
        values: JSONObject
    )
}

class HomeAssistantRemoteDataSourceImpl(
    private val clientProvider: () -> HassClient?
) : HomeAssistantRemoteDataSource {

    private var cachedAreas: Map<String, String>? = null

    override suspend fun getStates(): List<SmartHomeDevice> = withContext(Dispatchers.IO) {
        val client = clientProvider() ?: throw IllegalStateException("Chưa cấu hình Home Assistant")
        if (cachedAreas == null) {
            cachedAreas = runCatching { client.areas() }.getOrDefault(emptyMap())
        }
        val areas = cachedAreas ?: emptyMap()
        client.states().map { it.copy(area = areas[it.id].orEmpty()) }
    }

    override suspend fun getAreas(): Map<String, String> = withContext(Dispatchers.IO) {
        val client = clientProvider() ?: throw IllegalStateException("Chưa cấu hình Home Assistant")
        val areas = client.areas()
        cachedAreas = areas
        areas
    }

    override suspend fun callService(
        device: SmartHomeDevice,
        action: SmartHomeAction,
        values: JSONObject
    ): Unit = withContext(Dispatchers.IO) {
        val client = clientProvider() ?: throw IllegalStateException("Chưa cấu hình Home Assistant")
        client.call(device, action, values)
    }
}
