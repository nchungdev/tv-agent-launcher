package com.gathin.tvhome.feature.runningapps.data

import com.gathin.tvhome.feature.runningapps.data.RunningApp


interface AppRepository {
    suspend fun getRunningApps(): Result<List<RunningApp>>
    suspend fun forceStop(pkg: String): Result<Unit>
    suspend fun forceStopAll(packages: List<String>): Result<Unit>
}
