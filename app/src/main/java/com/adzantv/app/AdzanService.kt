package com.adzantv.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AdzanService : Service() {
    private var player: MediaPlayer? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val name = intent?.getStringExtra("name") ?: "Sholat"
        val ch = "adzan"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(ch, "Adzan", NotificationManager.IMPORTANCE_HIGH))

        val full = PendingIntent.getActivity(this, 2,
            Intent(this, AdzanActivity::class.java).putExtra("name", name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 3,
            Intent(this, AdzanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(this, ch)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Waktu $name telah tiba")
            .setContentText("Saatnya menunaikan sholat $name")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Matikan", stop)
            .setOngoing(true).build()

        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1, n)

        play()
        return START_NOT_STICKY
    }

    private fun play() {
        player?.release()
        val resId = resources.getIdentifier("adzan", "raw", packageName) // res/raw/adzan.mp3
        val mp = MediaPlayer()
        mp.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        if (resId != 0) {
            val afd = resources.openRawResourceFd(resId)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
        } else { // cadangan jika file adzan.mp3 belum dimasukkan
            mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        }
        mp.setOnCompletionListener { stopSelf() }
        mp.prepare(); mp.start()
        player = mp
    }

    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }

    companion object { const val ACTION_STOP = "com.adzantv.app.STOP" }
}
