package com.adzantv.app

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.net.ssl.HttpsURLConnection

/** Jam acuan yang tidak bergantung pada jam TV: NTP (UDP) lalu cadangan header HTTP Date. */
object TimeSync {
    /** selisih (ms) = waktu sebenarnya - System.currentTimeMillis() */
    fun offset(c: Context): Long {
        val p = Prefs.sp(c)
        if (!p.contains("s_off")) return Prefs.manualMin(c) * 60_000L
        val el = SystemClock.elapsedRealtime(); val e0 = p.getLong("s_el", 0)
        // Selama belum reboot, pakai jam monotonik: kebal terhadap perubahan jam sistem.
        return if (el >= e0) p.getLong("s_true", 0) + (el - e0) - System.currentTimeMillis()
        else p.getLong("s_off", 0)
    }

    fun synced(c: Context) = Prefs.sp(c).contains("s_off")
    fun now(c: Context): Long = System.currentTimeMillis() + offset(c)
    fun zone(c: Context): ZoneId = try { ZoneId.of(Prefs.zone(c)) } catch (e: Exception) { ZoneId.of("Asia/Jakarta") }
    fun today(c: Context): LocalDate = Instant.ofEpochMilli(now(c)).atZone(zone(c)).toLocalDate()

    fun sync(c: Context): Boolean {
        val off = ntp("time.google.com") ?: ntp("pool.ntp.org") ?: httpDate() ?: return false
        val sys = System.currentTimeMillis()
        Prefs.sp(c).edit().putLong("s_off", off).putLong("s_true", sys + off)
            .putLong("s_el", SystemClock.elapsedRealtime()).apply()
        return true
    }

    private fun ntp(host: String): Long? = try {
        DatagramSocket().use { s ->
            s.soTimeout = 3000
            val buf = ByteArray(48); buf[0] = 0x1B
            val t0 = System.currentTimeMillis()
            s.send(DatagramPacket(buf, 48, InetAddress.getByName(host), 123))
            s.receive(DatagramPacket(buf, 48))
            val t3 = System.currentTimeMillis()
            fun ts(o: Int): Long {
                var sec = 0L; var fr = 0L
                for (i in 0..3) sec = (sec shl 8) or (buf[o + i].toLong() and 0xFF)
                for (i in 4..7) fr = (fr shl 8) or (buf[o + i].toLong() and 0xFF)
                return (sec - 2208988800L) * 1000 + fr * 1000 / 0x100000000L
            }
            val t1 = ts(32); val t2 = ts(40)
            if (t2 < 1_600_000_000_000L) null else ((t1 - t0) + (t2 - t3)) / 2
        }
    } catch (e: Exception) { null }

    private fun httpDate(): Long? = try {
        val t0 = System.currentTimeMillis()
        val h = URL("https://www.google.com").openConnection() as HttpsURLConnection
        h.requestMethod = "HEAD"; h.connectTimeout = 5000; h.readTimeout = 5000
        val d = h.date; h.disconnect()
        val t3 = System.currentTimeMillis()
        if (d > 0) d + 500 - (t0 + t3) / 2 else null   // header Date dibulatkan ke detik
    } catch (e: Exception) { null }
}

/** Deteksi lokasi + zona waktu dari IP publik (TV umumnya tidak punya GPS). */
object Geo {
    private fun get(url: String): JSONObject? = try {
        val h = URL(url).openConnection() as HttpURLConnection
        h.connectTimeout = 6000; h.readTimeout = 6000
        val s = h.inputStream.bufferedReader().use { it.readText() }; h.disconnect()
        JSONObject(s)
    } catch (e: Exception) { null }

    fun detect(c: Context): Boolean {
        try {
            val a = get("https://ipwho.is/")
            val tz = a?.optJSONObject("timezone")?.optString("id").orEmpty()
            if (a != null && a.optBoolean("success") && tz.isNotEmpty()) {
                Prefs.save(c, a.optString("city", "Otomatis"), a.getDouble("latitude"), a.getDouble("longitude"),
                    tz, a.optString("country_code", ""), true)
                return true
            }
        } catch (e: Exception) { }
        try {
            // Cadangan lewat HTTP biasa, karena HTTPS gagal bila jam TV sangat salah (sertifikat dianggap kedaluwarsa)
            val b = get("http://ip-api.com/json/?fields=status,city,countryCode,lat,lon,timezone")
            if (b != null && b.optString("status") == "success") {
                Prefs.save(c, b.optString("city", "Otomatis"), b.getDouble("lat"), b.getDouble("lon"),
                    b.getString("timezone"), b.optString("countryCode", ""), true)
                return true
            }
        } catch (e: Exception) { }
        return false
    }
}

object Sync {
    /** Sinkron jam (+ lokasi bila mode otomatis), lalu jadwalkan ulang alarm. Jalan di thread latar. */
    fun run(c: Context, geo: Boolean, done: () -> Unit = {}) {
        val app = c.applicationContext
        Thread {
            TimeSync.sync(app)
            if (geo && Prefs.auto(app)) Geo.detect(app)
            try { Scheduler.scheduleNext(app) } catch (e: Exception) { }
            done()
        }.start()
    }
}
