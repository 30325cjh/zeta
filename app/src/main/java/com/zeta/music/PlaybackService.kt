package com.zeta.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.io.File

class PlaybackService : Service() {
    companion object {
        const val PLAY = "com.zeta.music.PLAY"
        const val PAUSE = "com.zeta.music.PAUSE"
        const val RESUME = "com.zeta.music.RESUME"
        const val STOP = "com.zeta.music.STOP"
        private const val CHANNEL = "zeta_music_playback"
        private const val NOTIFICATION_ID = 7101
    }
    private var player: MediaPlayer? = null
    private var currentTitle = "Zeta"
    private var paused = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "음악 재생", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> {
                val path = intent.getStringExtra("path") ?: return START_NOT_STICKY
                currentTitle = intent.getStringExtra("title") ?: File(path).name
                startForeground(NOTIFICATION_ID, buildNotification("재생 준비 중"))
                playFile(path)
            }
            PAUSE -> { runCatching { player?.pause() }; paused = true; updateNotification("일시정지") }
            RESUME -> { runCatching { player?.start() }; paused = false; updateNotification("재생 중") }
            STOP -> { releasePlayer(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun playFile(path: String) {
        releasePlayer()
        paused = false
        val file = File(path)
        if (!file.exists()) { stopSelf(); return }
        player = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(file.absolutePath)
            setOnPreparedListener { it.start(); updateNotification("재생 중") }
            setOnCompletionListener { updateNotification("재생 완료") }
            setOnErrorListener { _, _, _ -> updateNotification("재생 오류"); true }
            prepareAsync()
        }
    }

    private fun releasePlayer() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release(); player = null
    }

    private fun buildNotification(state: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val toggleAction = if (paused) RESUME else PAUSE
        val toggleLabel = if (paused) "계속 재생" else "일시정지"
        val toggleIcon = if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val toggle = PendingIntent.getService(this, 2, Intent(this, PlaybackService::class.java).setAction(toggleAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 3, Intent(this, PlaybackService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle).setContentText(state).setContentIntent(open)
            .setOngoing(state == "재생 중" || state == "재생 준비 중")
            .addAction(toggleIcon, toggleLabel, toggle)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "종료", stop).build()
    }

    private fun updateNotification(state: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
    }
    override fun onDestroy() { releasePlayer(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
