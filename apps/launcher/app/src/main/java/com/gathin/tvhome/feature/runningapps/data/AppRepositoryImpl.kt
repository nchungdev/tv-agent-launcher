package com.gathin.tvhome.feature.runningapps.data

import com.gathin.tvhome.feature.runningapps.data.AppLocalDataSource
import com.gathin.tvhome.feature.runningapps.data.RunningApp
import com.gathin.tvhome.feature.runningapps.data.AppRepository


class AppRepositoryImpl(
    private val localDataSource: AppLocalDataSource
) : AppRepository {

    override suspend fun getRunningApps(): Result<List<RunningApp>> = runCatching {
        localDataSource.getRunningApps()
    }

    override suspend fun forceStop(pkg: String): Result<Unit> = runCatching {
        localDataSource.forceStop(pkg)
    }

    override suspend fun forceStopAll(packages: List<String>): Result<Unit> = runCatching {
        localDataSource.forceStopAll(packages)
    }
}
