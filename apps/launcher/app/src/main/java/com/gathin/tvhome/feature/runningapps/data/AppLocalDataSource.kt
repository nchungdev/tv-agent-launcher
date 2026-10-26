package com.gathin.tvhome.feature.runningapps.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.runningapps.data.BackgroundApps
import com.gathin.tvhome.feature.main.data.LocalAdb
import com.gathin.tvhome.feature.runningapps.data.RunningApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class AppLocalDataSource(private val context: Context) {
    private val adb = LocalAdb(context)
    private val myPackage = context.packageName
    private var cachedLaunchable: Set<String>? = null

    suspend fun getRunningApps(): List<RunningApp> = withContext(Dispatchers.IO) {
        if (cachedLaunchable == null) {
            cachedLaunchable = queryLaunchablePackages()
        }
        val allowed = cachedLaunchable ?: emptySet()
        val rawApps = try {
            BackgroundApps.list(adb) { allowed.contains(it) }
        } catch (_: Exception) {
            getRunningAppsFromActivityManager(allowed)
        }
        val packageManager = context.packageManager

        rawApps.mapNotNull { raw ->
            val isLauncher = raw.pkg == myPackage
            val label: CharSequence = if (isLauncher) {
                context.getString(R.string.launcher_self_title)
            } else {
                try {
                    val info = packageManager.getApplicationInfo(raw.pkg, 0)
                    packageManager.getApplicationLabel(info)
                } catch (_: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
            }
            val icon: Drawable? = try {
                packageManager.getApplicationIcon(raw.pkg)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }

            RunningApp(
                pkg = raw.pkg,
                label = label,
                icon = icon,
                memoryKb = raw.memoryKb,
                cpuPercent = raw.cpuPercent,
                recency = raw.recency,
                isLauncher = isLauncher
            )
        }
    }

    suspend fun forceStop(pkg: String): Unit = withContext(Dispatchers.IO) {
        BackgroundApps.stop(adb, listOf(pkg))
    }

    suspend fun forceStopAll(packages: List<String>): Unit = withContext(Dispatchers.IO) {
        val targets = packages.filter { it != myPackage }
        BackgroundApps.stop(adb, targets)
    }

    private fun getRunningAppsFromActivityManager(allowed: Set<String>): List<BackgroundApps.App> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return emptyList()
        val procs = am.runningAppProcesses ?: return emptyList()
        val result = ArrayList<BackgroundApps.App>()
        val seen = HashSet<String>()
        for (proc in procs) {
            for (pkg in proc.pkgList ?: emptyArray()) {
                if (allowed.contains(pkg) && seen.add(pkg)) {
                    result.add(BackgroundApps.App(pkg, 0L, 0))
                }
            }
        }
        return result
    }

    private fun queryLaunchablePackages(): Set<String> {
        val pm = context.packageManager
        val pkgs = mutableSetOf<String>()
        val categories = arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                info.activityInfo?.let { pkgs.add(it.packageName) }
            }
        }
        pkgs.add(myPackage)
        val ime = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )
        if (ime != null) pkgs.remove(ime.substringBefore('/'))
        return pkgs
    }
}
