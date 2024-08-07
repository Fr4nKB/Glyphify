package com.frank.glyphify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AppUpdater(private val context: Context, workerParams: WorkerParameters):
    Worker(context, workerParams) {

    companion object {
        val versionUrl = "https://api.github.com/repos/Fr4nKB/Glyphify/releases/latest"
        val downloadUrl = "https://github.com/Fr4nKB/Glyphify/releases/latest"
    }

    override fun doWork(): Result {
        val client = OkHttpClient()
        val request = Request.Builder().url(versionUrl).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.failure()

            val jsonObject = JSONObject(response.body!!.string())
            val latestVersion = jsonObject.getString("tag_name").substring(1)

            // get the current version of the app
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0)
                .versionName

            val current = currentVersion.split(".").map { it.toInt() }
            val latest = latestVersion.split(".").map { it.toInt() }

            val minLength = minOf(current.size, latest.size)
            var update = latest.size > current.size

            // compare the two versions
            for (i in 0 until minLength) {
                val currentPart = current.getOrElse(i) { 0 }
                val latestPart = latest.getOrElse(i) { 0 }

                if (currentPart < latestPart) {
                    update = true
                    break
                }
                else if(currentPart > latestPart) return Result.success()
            }

            if(update) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                val pendingIntent = PendingIntent.getActivity(context, 0, browserIntent,
                    PendingIntent.FLAG_IMMUTABLE)

                val builder = NotificationCompat.Builder(context, context.getString(R.string.app_name))
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(context.getString(R.string.title_new_version))
                    .setContentText(context.getString(R.string.msg_new_version))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                with(NotificationManagerCompat.from(context)) {
                    notify(1, builder.build())
                }
            }
        }


        return Result.success()
    }
}
