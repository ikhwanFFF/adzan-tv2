package com.adzantv.app

import android.content.Context
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.*

data class Prayer(val name: String, val timeMillis: Long)

/** Hitung waktu sholat offline (metode Kemenag RI: Subuh 20°, Isya 18°, Ashar mazhab Syafi'i, ihtiyath +2 menit). */
object PrayerTimes {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun fix(a: Double, m: Double): Double { val r = a - m * floor(a / m); return r }

    private fun julian(y: Int, m: Int, d: Int): Double {
        var yy = y; var mm = m
        if (mm <= 2) { yy -= 1; mm += 12 }
        val a = floor(yy / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (yy + 4716)) + floor(30.6001 * (mm + 1)) + d + b - 1524.5
    }

    private fun sun(jd: Double): Pair<Double, Double> { // (deklinasi, equation of time dalam jam)
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15.0, 24.0)
        val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
        val eqt = q / 15.0 - ra
        return decl to (eqt + if (eqt > 12) -24 else if (eqt < -12) 24 else 0).toDouble()
    }

    fun forDate(date: LocalDate, lat: Double, lon: Double, fajr: Double = 20.0, isha: Double = 18.0, ihMin: Int = 2): List<Prayer> {
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lon / 360.0
        val (decl, eqt) = sun(jd)
        val noonUtc = 12.0 - lon / 15.0 - eqt // jam UTC

        fun angleTime(angle: Double): Double {
            val x = (-sin(rad(angle)) - sin(rad(decl)) * sin(rad(lat))) / (cos(rad(decl)) * cos(rad(lat)))
            return deg(acos(x.coerceIn(-1.0, 1.0))) / 15.0
        }
        val asrAngle = -deg(atan(1.0 / (1.0 + tan(rad(abs(lat - decl))))))

        val ih = ihMin / 60.0 // ihtiyath
        val times = listOf(
            "Subuh" to noonUtc - angleTime(fajr) + ih,
            "Dzuhur" to noonUtc + ih,
            "Ashar" to noonUtc + angleTime(asrAngle) + ih,
            "Maghrib" to noonUtc + angleTime(0.833) + ih,
            "Isya" to noonUtc + angleTime(-isha) + ih
        )
        val base = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return times.map { (n, h) -> Prayer(n, base + (h * 3600000.0).toLong()) }
    }

    /** Indonesia: Kemenag (20°/18°, +2 menit). Negara lain: Muslim World League (18°/17°). */
    fun forPlace(c: Context, date: LocalDate): List<Prayer> {
        val id = Prefs.country(c) == "ID"
        return forDate(date, Prefs.lat(c), Prefs.lon(c), if (id) 20.0 else 18.0, if (id) 18.0 else 17.0, if (id) 2 else 0)
    }

    /** Waktu sholat berikutnya menurut jam acuan (bukan jam TV). */
    fun next(c: Context): Prayer {
        val now = TimeSync.now(c)
        val today = TimeSync.today(c)
        for (i in -1..2) {
            forPlace(c, today.plusDays(i.toLong())).firstOrNull { it.timeMillis > now + 1000 }?.let { return it }
        }
        error("tidak ada waktu sholat")
    }
}
