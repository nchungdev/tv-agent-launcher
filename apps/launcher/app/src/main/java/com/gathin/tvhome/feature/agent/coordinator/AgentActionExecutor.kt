package com.gathin.tvhome.feature.agent.coordinator
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.ui.*
import com.gathin.tvhome.feature.agent.widget.*

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.agent.data.ActionStatus
import com.gathin.tvhome.feature.agent.data.ExecutionResult
import java.util.Locale


object AgentActionExecutor {
    const val PLEX_PACKAGE = "com.plexapp.android"
    const val YOUTUBE_PACKAGE = "com.google.android.youtube.tv"
    private const val NETFLIX_PACKAGE = "com.netflix.ninja"

    fun execute(context: Context?, notification: TvNotification): ExecutionResult {
        val rawAction = notification.actionType?.trim().orEmpty()
        val params = HashMap<String, Any?>()
        notification.actionParams?.let { params.putAll(it) }

        val combined = "${notification.source} ${notification.title} ${notification.message}"
            .lowercase(Locale.ROOT)
        if (resolvePackage(params).isNullOrEmpty()) {
            when {
                combined.contains("plex") || combined.contains("radarr") ||
                    combined.contains("sonarr") || combined.contains("phim") ->
                    params["package_name"] = PLEX_PACKAGE
                combined.contains("youtube") -> params["package_name"] = YOUTUBE_PACKAGE
                combined.contains("netflix") -> params["package_name"] = NETFLIX_PACKAGE
            }
        }
        val effectiveAction = when {
            rawAction.isNotEmpty() -> rawAction
            !resolvePackage(params).isNullOrEmpty() || !resolveUri(params).isNullOrEmpty() ->
                "app.open"
            else -> ""
        }
        return execute(context, notification.id, effectiveAction, params)
    }

    private fun resolvePackage(parameters: Map<String, Any?>): String? {
        val raw = parameters["package_name"]
            ?: parameters["package"]
            ?: parameters["app_id"]
            ?: parameters["pkg"]
        return raw?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun resolveUri(parameters: Map<String, Any?>): String? {
        val raw = parameters["deep_link"] ?: parameters["uri"] ?: parameters["url"]
        return raw?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun executeAction(
        context: Context?,
        actionType: String,
        parameters: Map<String, Any?> = emptyMap()
    ): ExecutionResult = execute(context, "bot-action", actionType, parameters)

    fun execute(
        context: Context?,
        stepId: String,
        actionType: String,
        parameters: Map<String, Any?>
    ): ExecutionResult {
        if (context == null) {
            return failed(stepId, "NO_CONTEXT", "Context cannot be null")
        }
        val norm = actionType.lowercase(Locale.ROOT).trim()
        return when (norm) {
            "plex.open", "plex" -> executePlexOpen(context, stepId, parameters)
            "youtube.launch", "youtube" -> executeYoutubeLaunch(context, stepId, parameters)
            "app.open", "launch_app", "open_app", "deeplink", "open", "launch",
            "media.open", "play" -> executeDeepLinkOrApp(context, stepId, parameters)
            else -> if (!resolvePackage(parameters).isNullOrEmpty() ||
                !resolveUri(parameters).isNullOrEmpty()
            ) {
                executeDeepLinkOrApp(context, stepId, parameters)
            } else {
                failed(stepId, "UNSUPPORTED_ACTION", "Unsupported action type: $actionType")
            }
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getTvLaunchIntent(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return intent
    }

    fun buildPlexIntent(context: Context, parameters: Map<String, Any?>): Intent? {
        val uriString = resolveUri(parameters)
        if (!uriString.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
            intent.setPackage(PLEX_PACKAGE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                return intent
            }
        }
        return getTvLaunchIntent(context, PLEX_PACKAGE)
    }

    fun buildYoutubeIntent(context: Context, parameters: Map<String, Any?>): Intent? {
        val videoId = parameters["video_id"] as? String
        if (!videoId.isNullOrBlank()) {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.setPackage(YOUTUBE_PACKAGE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }
        val query = parameters["query"] as? String
        if (!query.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_SEARCH)
            intent.setPackage(YOUTUBE_PACKAGE)
            intent.putExtra("query", query)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }
        return getTvLaunchIntent(context, YOUTUBE_PACKAGE)
    }

    private fun executeDeepLinkOrApp(
        context: Context,
        stepId: String,
        parameters: Map<String, Any?>
    ): ExecutionResult {
        val targetPkg = resolvePackage(parameters)
        val uriStr = resolveUri(parameters)

        if (targetPkg == PLEX_PACKAGE) return executePlexOpen(context, stepId, parameters)
        if (targetPkg == YOUTUBE_PACKAGE) return executeYoutubeLaunch(context, stepId, parameters)

        if (!uriStr.isNullOrEmpty()) {
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                    if (!targetPkg.isNullOrEmpty()) setPackage(targetPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
                return succeeded(stepId, "uri" to uriStr)
            } catch (_: Exception) {}
        }
        return executeAppOpen(context, stepId, parameters)
    }

    private fun executePlexOpen(
        context: Context,
        stepId: String,
        parameters: Map<String, Any?>
    ): ExecutionResult {
        if (!isAppInstalled(context, PLEX_PACKAGE)) {
            return failed(stepId, "APP_NOT_INSTALLED", "Plex app is not installed on this device")
        }
        val primary = buildPlexIntent(context, parameters)
        if (primary != null) {
            try {
                context.startActivity(primary)
                return succeeded(stepId, "package" to PLEX_PACKAGE)
            } catch (_: Exception) {}
        }
        return try {
            val fallback = getTvLaunchIntent(context, PLEX_PACKAGE)
                ?: return failed(stepId, "LAUNCH_INTENT_NULL", "Failed to create launch intent")
            context.startActivity(fallback)
            succeeded(stepId, "package" to PLEX_PACKAGE)
        } catch (error: Exception) {
            failed(stepId, "LAUNCH_EXCEPTION", error.message)
        }
    }

    private fun executeYoutubeLaunch(
        context: Context,
        stepId: String,
        parameters: Map<String, Any?>
    ): ExecutionResult {
        if (!isAppInstalled(context, YOUTUBE_PACKAGE)) {
            return failed(stepId, "APP_NOT_INSTALLED", "YouTube app is not installed")
        }
        return try {
            val intent = buildYoutubeIntent(context, parameters)
                ?: return failed(stepId, "LAUNCH_INTENT_NULL", "Failed to create launch intent")
            context.startActivity(intent)
            succeeded(stepId, "package" to YOUTUBE_PACKAGE)
        } catch (error: Exception) {
            failed(stepId, "LAUNCH_EXCEPTION", error.message)
        }
    }

    private fun executeAppOpen(
        context: Context,
        stepId: String,
        parameters: Map<String, Any?>
    ): ExecutionResult {
        val targetPkg = resolvePackage(parameters)
        if (targetPkg.isNullOrBlank()) {
            return failed(stepId, "MISSING_PACKAGE_NAME", "Missing package_name in parameters")
        }
        if (!isAppInstalled(context, targetPkg)) {
            return failed(stepId, "APP_NOT_INSTALLED", "App $targetPkg is not installed")
        }
        return try {
            val launch = getTvLaunchIntent(context, targetPkg)
                ?: return failed(stepId, "LAUNCH_INTENT_NULL", "Could not find launch intent")
            context.startActivity(launch)
            succeeded(stepId, "package" to targetPkg)
        } catch (error: Exception) {
            failed(stepId, "LAUNCH_EXCEPTION", error.message)
        }
    }

    private fun succeeded(stepId: String, state: Pair<String, Any>): ExecutionResult =
        ExecutionResult(
            stepId = stepId,
            status = ActionStatus.SUCCEEDED,
            actualState = mapOf(state)
        )

    private fun failed(stepId: String, errorCode: String, detail: String?): ExecutionResult =
        ExecutionResult(
            stepId = stepId,
            status = ActionStatus.FAILED,
            errorCode = errorCode,
            detail = detail
        )
}
