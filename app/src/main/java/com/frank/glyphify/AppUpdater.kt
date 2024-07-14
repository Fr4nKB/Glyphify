package com.frank.glyphify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
            if (!response.isSuccessful) throw RuntimeException("Unexpected code $response")

            val jsonObject = JSONObject(response.body!!.string())
            val latestVersion = jsonObject.getString("tag_name").substring(1)

            // get the current version of the app
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0)
                .versionName

            val current = currentVersion.split(".").map { it.toInt() }
            val latest = latestVersion.split(".").map { it.toInt() }

            for (i in latest.indices) {
                if (i >= current.size || current[i] < latest[i]) {
                    // intent to open browser at download page
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
                        notify(69, builder.build())
                    }
                }
            }
        }


        return Result.success()
    }
}
