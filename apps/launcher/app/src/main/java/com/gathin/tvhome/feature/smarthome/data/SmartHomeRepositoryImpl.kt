package com.gathin.tvhome.feature.smarthome.data

import com.gathin.tvhome.feature.smarthome.data.HomeAssistantRemoteDataSource
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import com.gathin.tvhome.feature.smarthome.data.SmartHomeRepository
import org.json.JSONObject


class SmartHomeRepositoryImpl(
    private val remoteDataSource: HomeAssistantRemoteDataSource
) : SmartHomeRepository {

    override suspend fun getDevices(): Result<List<SmartHomeDevice>> = runCatching {
        remoteDataSource.getStates()
    }

    override suspend fun getAreas(): Result<Map<String, String>> = runCatching {
        remoteDataSource.getAreas()
    }

    override suspend fun executeAction(
        device: SmartHomeDevice,
        action: SmartHomeAction,
        values: JSONObject
    ): Result<Unit> = runCatching {
        remoteDataSource.callService(device, action, values)
    }
}
