package com.adzantv.app

import android.content.Context
import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.net.ssl.HttpsURLConnection

object TimeSync {

    fun synced(c: Context): Boolean =
        Prefs.sp(c).contains("s_true")

    /**
     * Waktu sebenarnya hasil NTP
     */
    fun now(c: Context): Long {
        val p = Prefs.sp(c)

        if (!p.contains("s_true")) {
            return System.currentTimeMillis() +
                    (Prefs.manualMin(c) * 60_000L)
        }

        val baseTrue = p.getLong("s_true", 0L)
        val baseElapsed = p.getLong("s_el", 0L)

        return baseTrue +
                (SystemClock.elapsedRealtime() - baseElapsed)
    }

    /**
     * Tetap dipertahankan bila masih dipakai bagian lain
     */
    fun offset(c: Context): Long {
        return now(c) - System.currentTimeMillis()
    }

    fun zone(c: Context): ZoneId =
        try {
            ZoneId.of(Prefs.zone(c))
        } catch (_: Exception) {
            ZoneId.of("Asia/Jakarta")
        }

    fun today(c: Context): LocalDate =
        Instant.ofEpochMilli(now(c))
            .atZone(zone(c))
            .toLocalDate()

    fun sync(c: Context): Boolean {

        val utcNow =
            bestNtp("time.google.com")
                ?: bestNtp("pool.ntp.org")
                ?: httpDate()
                ?: return false

        Prefs.sp(c)
            .edit()
            .putLong("s_true", utcNow)
            .putLong("s_el", SystemClock.elapsedRealtime())
            .apply()

        return true
    }

    private fun bestNtp(host: String): Long? {

        var bestTime: Long? = null
        var bestDelay = Long.MAX_VALUE

        repeat(5) {

            val result = ntpOnce(host) ?: return@repeat

            if (result.delay < bestDelay) {
                bestDelay = result.delay
                bestTime = result.trueTime
            }
        }

        return bestTime
    }

    data class NtpResult(
        val trueTime: Long,
        val delay: Long
    )

    private fun ntpOnce(host: String): NtpResult? {
        return try {

            DatagramSocket().use { socket ->

                socket.soTimeout = 3000

                val buf = ByteArray(48)
                buf[0] = 0x1B

                val t0 = System.currentTimeMillis()

                socket.send(
                    DatagramPacket(
                        buf,
                        buf.size,
                        InetAddress.getByName(host),
                        123
                    )
                )

                socket.receive(
                    DatagramPacket(
                        buf,
                        buf.size
                    )
                )

                val t3 = System.currentTimeMillis()

                fun readTimestamp(offset: Int): Long {

                    var sec = 0L
                    var frac = 0L

                    for (i in 0..3) {
                        sec = (sec shl 8) or
                                (buf[offset + i].toLong() and 0xFF)
                    }

                    for (i in 4..7) {
                        frac = (frac shl 8) or
                                (buf[offset + i].toLong() and 0xFF)
                    }

                    return ((sec - 2208988800L) * 1000L) +
                            ((frac * 1000L) / 0x100000000L)
                }

                val t1 = readTimestamp(32)
                val t2 = readTimestamp(40)

                if (t2 < 1600000000000L) {
                    return null
                }

                val offset =
                    ((t1 - t0) + (t2 - t3)) / 2

                val delay =
                    (t3 - t0) - (t2 - t1)

                NtpResult(
                    trueTime = t3 + offset,
                    delay = delay
                )
            }

        } catch (_: Exception) {
            null
        }
    }

    private fun httpDate(): Long? {
        return try {

            val start = System.currentTimeMillis()

            val conn =
                URL("https://www.google.com")
                    .openConnection() as HttpsURLConnection

            conn.requestMethod = "HEAD"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val serverDate = conn.date

            conn.disconnect()

            val end = System.currentTimeMillis()

            if (serverDate > 0) {
                serverDate + 500 - ((start + end) / 2)
            } else {
                null
            }

        } catch (_: Exception) {
            null
        }
    }
}
