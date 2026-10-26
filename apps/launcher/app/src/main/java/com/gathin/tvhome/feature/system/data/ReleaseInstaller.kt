package com.gathin.tvhome.feature.system.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.gathin.tvhome.receiver.InstallStatusReceiver
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.function.IntConsumer


object ReleaseInstaller {

    private const val SESSION_FILE = "tv-launcher.apk"

    @JvmStatic
    @Throws(IOException::class)
    fun install(context: Context, release: LauncherRelease, percentDone: IntConsumer) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(release.apkSize)
        val sessionId = installer.createSession(params)

        installer.openSession(sessionId).use { session ->
            try {
                val downloadedSha256 = download(release, session, percentDone)
                if (!downloadedSha256.equalsIgnoreCase(release.apkSha256)) {
                    throw IOException("checksum mismatch")
                }
                session.commit(statusReceiver(context, sessionId).intentSender)
            } catch (e: Throwable) {
                session.abandon()
                if (e is IOException) throw e
                throw IOException(e)
            }
        }
    }

    private fun download(
        release: LauncherRelease,
        session: PackageInstaller.Session,
        percentDone: IntConsumer
    ): String {
        val connection = LauncherRelease.open(release.apkUrl)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP " + connection.responseCode)
            }

            val sha256 = sha256()
            connection.inputStream.use { body ->
                session.openWrite(SESSION_FILE, 0, release.apkSize).use { apk ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var reportedPercent = -1
                    var read: Int
                    while (body.read(buffer).also { read = it } > 0) {
                        apk.write(buffer, 0, read)
                        sha256.update(buffer, 0, read)
                        written += read
                        if (written > release.apkSize) throw IOException("download too large")

                        val percent = (written * 100 / release.apkSize).toInt()
                        if (percent != reportedPercent) percentDone.accept(percent)
                        reportedPercent = percent
                    }

                    if (written != release.apkSize) throw IOException("download incomplete")
                    session.fsync(apk)
                }
            }
            return hex(sha256.digest())
        } finally {
            connection.disconnect()
        }
    }

    private fun statusReceiver(context: Context, sessionId: Int): PendingIntent {
        val status = Intent(context, InstallStatusReceiver::class.java)
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context, sessionId, status,
            PendingIntent.FLAG_UPDATE_CURRENT or mutable
        )
    }

    private fun sha256(): MessageDigest {
        return try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw IOException(e)
        }
    }

    private fun hex(bytes: ByteArray): String {
        val text = StringBuilder(bytes.size * 2)
        for (value in bytes) text.append(String.format("%02x", value.toInt() and 0xff))
        return text.toString()
    }

    private fun String.equalsIgnoreCase(other: String?): Boolean =
        this.equals(other, ignoreCase = true)
}
