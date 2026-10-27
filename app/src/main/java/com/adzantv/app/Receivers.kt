package com.adzantv.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val name = i.getStringExtra("name") ?: return
        if (name.isNotEmpty()) {
            ContextCompat.startForegroundService(c, Intent(c, AdzanService::class.java).putExtra("name", name))
        }
        Scheduler.scheduleNext(c) // jadwalkan sholat berikutnya
        val pr = goAsync()
        Sync.run(c, geo = false) { pr.finish() } // segarkan jam acuan tiap waktu sholat
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.scheduleNext(c)
        Sync.run(c, geo = true)
    }
}
