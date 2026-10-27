package com.adzantv.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    internal fun sp(c: Context): SharedPreferences = c.getSharedPreferences("adzan", Context.MODE_PRIVATE)
    fun lat(c: Context) = sp(c).getFloat("lat", -6.2088f).toDouble()   // default Jakarta
    fun lon(c: Context) = sp(c).getFloat("lon", 106.8456f).toDouble()
    fun city(c: Context) = sp(c).getString("city", "Jakarta") ?: "Jakarta"
    fun zone(c: Context) = sp(c).getString("zone", "Asia/Jakarta") ?: "Asia/Jakarta"
    fun country(c: Context) = sp(c).getString("country", "ID") ?: "ID"
    fun auto(c: Context) = sp(c).getBoolean("auto", true)          // lokasi otomatis via internet
    fun manualMin(c: Context) = sp(c).getInt("manual_min", 0)       // koreksi jam manual (menit)
    fun setManualMin(c: Context, v: Int) = sp(c).edit().putInt("manual_min", v).apply()

    fun save(c: Context, city: String, lat: Double, lon: Double, zone: String, country: String, auto: Boolean) =
        sp(c).edit().putString("city", city).putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat())
            .putString("zone", zone).putString("country", country).putBoolean("auto", auto).apply()

    fun setAuto(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto", v).apply()

    var Context.enabled: Boolean
        get() = sp(this).getBoolean("enabled", true)
        set(v) { sp(this).edit().putBoolean("enabled", v).apply() }
}
