package dev.pgaxis.musicaxs.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.pgaxis.musicaxs.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

class PodcastDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "podcast_downloads"
        const val ACTION_DOWNLOAD = "dev.pgaxis.musicaxs.ACTION_DOWNLOAD"
        const val ACTION_CANCEL = "dev.pgaxis.musicaxs.ACTION_CANCEL"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun startDownload(context: Context, audioUrl: String, episodeTitle: String) {
            val intent = Intent(context, PodcastDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_URL, audioUrl)
                putExtra(EXTRA_TITLE, episodeTitle)
                putExtra(EXTRA_NOTIFICATION_ID, audioUrl.hashCode())
            }
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>()
    private val client = OkHttpClient()
    private lateinit var notificationManager: NotificationManager
    private var lastProgressUpdate = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, url.hashCode())
                startDownload(url, title, notifId)
            }
            ACTION_CANCEL -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, url.hashCode())
                cancelDownload(url, notifId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String, title: String, notifId: Int) {
        val notification = buildNotification(title, 0, url, notifId)
        startForeground(notifId, notification)

        val job = serviceScope.launch {
            try {
                PodcastDownloader.setDownloading(url, true)
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body ?: return@launch
                val totalBytes = body.contentLength()
                val file = PodcastDownloader.fileFor(this@PodcastDownloadService, url)
                file.parentFile?.mkdirs()

                var downloadedBytes = 0L
                file.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate >= 500L) {
                                    val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                    notificationManager.notify(
                                        notifId,
                                        buildNotification(title, progress, url, notifId)
                                    )
                                    lastProgressUpdate = now
                                }
                            }
                        }
                    }
                }
                notificationManager.notify(notifId, buildDoneNotification(title, notifId))
            } catch (_: Exception) {
                notificationManager.cancel(notifId)
                PodcastDownloader.fileFor(this@PodcastDownloadService, url).delete()
            } finally {
                PodcastDownloader.setDownloading(url, false)
                activeJobs.remove(url)
                if (activeJobs.isEmpty()) stopSelf()
            }
        }
        activeJobs[url] = job
    }

    private fun cancelDownload(url: String, notifId: Int) {
        activeJobs[url]?.cancel()
        activeJobs.remove(url)
        PodcastDownloader.fileFor(this, url).delete()
        PodcastDownloader.setDownloading(url, false)
        notificationManager.cancel(notifId)
        if (activeJobs.isEmpty()) stopSelf()
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        url: String,
        notifId: Int
    ): android.app.Notification {
        val cancelIntent = Intent(this, PodcastDownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, notifId,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.download_done)
            .setContentTitle(title)
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .addAction(R.drawable.cross, getString(R.string.cancel), cancelPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun buildDoneNotification(title: String, notifId: Int): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.download_done)
            .setContentTitle(title)
            .setContentText(getString(R.string.download_complete))
            .setAutoCancel(true)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Podcast downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of podcast episode downloads"
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}