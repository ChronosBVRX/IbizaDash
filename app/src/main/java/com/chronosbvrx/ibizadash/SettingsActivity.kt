package com.chronosbvrx.ibizadash

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        findViewById<TextView>(R.id.settingsVersion).text = "v${versionName ?: "—"}"

        val showWeather = findViewById<CheckBox>(R.id.showWeather)
        val demoMedia = findViewById<CheckBox>(R.id.demoMedia)
        val unitKmh = findViewById<RadioButton>(R.id.unitKmh)
        val unitMph = findViewById<RadioButton>(R.id.unitMph)

        showWeather.isChecked = Prefs.showWeather(this)
        demoMedia.isChecked = Prefs.demoMedia(this)
        if (Prefs.useMph(this)) {
            unitMph.isChecked = true
        } else {
            unitKmh.isChecked = true
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.setShowWeather(this, showWeather.isChecked)
            Prefs.setDemoMedia(this, demoMedia.isChecked)
            Prefs.setUseMph(this, unitMph.isChecked)
            finish()
        }

        findViewById<Button>(R.id.btnCarChoose).setOnClickListener {
            launch("com.tw.carchoose", "com.tw.carchoose.QueryCarActivity")
        }
        findViewById<Button>(R.id.btnCarSettings).setOnClickListener {
            launch("com.dofun.carsetting", "com.dofun.carsetting.ui.MainActivity")
        }
        findViewById<Button>(R.id.btnReverse).setOnClickListener {
            launch("com.tw.reverse", "com.tw.reverse.ReverseActivity")
        }
        findViewById<Button>(R.id.btnNotificationAccess).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Ajustes de acceso a notificaciones no disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launch(packageName: String, activity: String) {
        try {
            startActivity(
                Intent()
                    .setComponent(ComponentName(packageName, activity))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "No disponible: $packageName", Toast.LENGTH_SHORT).show()
        }
    }
}
