package com.adzantv.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AdzanActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val name = intent.getStringExtra("name") ?: "Sholat"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B3D2E"))
        }
        root.addView(TextView(this).apply {
            text = "Waktu $name"; textSize = 64f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Saatnya menunaikan sholat"; textSize = 28f; setTextColor(Color.parseColor("#CFE8DC")); gravity = Gravity.CENTER
            setPadding(0, 16, 0, 48)
        })
        val btn = Button(this).apply {
            text = "Matikan Adzan"; textSize = 24f
            setOnClickListener {
                startService(Intent(this@AdzanActivity, AdzanService::class.java).setAction(AdzanService.ACTION_STOP))
                finish()
            }
        }
        root.addView(btn); setContentView(root); btn.requestFocus()
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 5 * 60 * 1000L)
    }
}
