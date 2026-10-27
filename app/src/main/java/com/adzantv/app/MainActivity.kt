package com.adzantv.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.core.content.ContextCompat
import com.adzantv.app.Prefs.enabled
import java.time.Instant
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var info: TextView
    private val cities = linkedMapOf(
        "Jakarta" to Triple(-6.2088, 106.8456, "Asia/Jakarta"), "Bekasi" to Triple(-6.2383, 106.9756, "Asia/Jakarta"),
        "Bandung" to Triple(-6.9175, 107.6191, "Asia/Jakarta"), "Semarang" to Triple(-6.9667, 110.4167, "Asia/Jakarta"),
        "Yogyakarta" to Triple(-7.7956, 110.3695, "Asia/Jakarta"), "Surabaya" to Triple(-7.2575, 112.7521, "Asia/Jakarta"),
        "Medan" to Triple(3.5952, 98.6722, "Asia/Jakarta"), "Palembang" to Triple(-2.9761, 104.7754, "Asia/Jakarta"),
        "Makassar" to Triple(-5.1477, 119.4327, "Asia/Makassar"), "Denpasar" to Triple(-8.6705, 115.2126, "Asia/Makassar")
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(80, 48, 80, 48) }
        root.addView(TextView(this).apply { text = "Adzan TV"; textSize = 36f })
        info = TextView(this).apply { textSize = 22f; setPadding(0, 24, 0, 24) }
        root.addView(info)

        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }.also { root.addView(it) }
        val first = btn("Lokasi & Jam Otomatis (Internet)") { autoNow() }
        btn("Pilih Kota Manual") { pickCity() }
        btn("Koreksi Jam Manual (bila tanpa internet)") { pickOffset() }
        btn("Aktif / Nonaktif") { enabled = !enabled; Scheduler.scheduleNext(this); refresh() }
        btn("Tes Adzan Sekarang") { ContextCompat.startForegroundService(this, Intent(this, AdzanService::class.java).putExtra("name", "Tes")) }
        btn("Izinkan Alarm Tepat Waktu") {
            if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        first.requestFocus()

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Scheduler.scheduleNext(this)
        Sync.run(this, geo = true) { runOnUiThread { refresh() } }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun autoNow() {
        Prefs.setAuto(this, true)
        Toast.makeText(this, "Mencari jam & lokasi dari internet…", Toast.LENGTH_SHORT).show()
        Sync.run(this, geo = true) { runOnUiThread {
            refresh()
            Toast.makeText(this, if (TimeSync.synced(this)) "Jam & lokasi diperbarui" else "Gagal: tidak ada internet", Toast.LENGTH_LONG).show()
        } }
    }

    private fun pickCity() {
        val names = cities.keys.toTypedArray()
        AlertDialog.Builder(this).setTitle("Pilih Kota").setItems(names) { _, i ->
            val (la, lo, z) = cities[names[i]]!!
            Prefs.save(this, names[i], la, lo, z, "ID", false); Scheduler.scheduleNext(this); refresh()
        }.show()
    }

    private fun pickOffset() {
        val items = arrayOf("+1 menit", "-1 menit", "+10 menit", "-10 menit", "+1 jam", "-1 jam", "Reset (0)")
        val delta = intArrayOf(1, -1, 10, -10, 60, -60, 0)
        AlertDialog.Builder(this).setTitle("Koreksi jam (hanya dipakai bila belum pernah sinkron internet)").setItems(items) { _, i ->
            Prefs.setManualMin(this, if (i == 6) 0 else Prefs.manualMin(this) + delta[i])
            Scheduler.scheduleNext(this); refresh()
        }.show()
    }

    private fun refresh() {
        val f = DateTimeFormatter.ofPattern("HH:mm").withZone(TimeSync.zone(this))
        val full = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(TimeSync.zone(this))
        val list = PrayerTimes.forPlace(this, TimeSync.today(this))
            .joinToString("\n") { "${it.name.padEnd(8)} ${f.format(Instant.ofEpochMilli(it.timeMillis))}" }
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val src = if (TimeSync.synced(this)) "internet (NTP)" else "jam TV${if (Prefs.manualMin(this) != 0) " ${Prefs.manualMin(this)} mnt (manual)" else " (belum sinkron)"}"
        info.text = "Kota: ${Prefs.city(this)} (${if (Prefs.auto(this)) "otomatis" else "manual"}), ${Prefs.zone(this)}\n" +
            "Jam acuan: ${full.format(Instant.ofEpochMilli(TimeSync.now(this)))}  ← $src\n" +
            "Status: ${if (enabled) "AKTIF" else "NONAKTIF"}" +
            (if (!exactOk) "  (izin alarm tepat waktu belum diberikan)" else "") + "\n\n$list"
    }
}
