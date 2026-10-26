package com.gathin.tvhome.feature.smarthome.data

import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import org.json.JSONObject


interface SmartHomeRepository {
    suspend fun getDevices(): Result<List<SmartHomeDevice>>
    suspend fun getAreas(): Result<Map<String, String>>
    suspend fun executeAction(
        device: SmartHomeDevice,
        action: SmartHomeAction,
        values: JSONObject = JSONObject()
    ): Result<Unit>
}
