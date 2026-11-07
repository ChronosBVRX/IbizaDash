package com.chronosbvrx.ibizadash

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class AppsActivity : AppCompatActivity() {

    private lateinit var grid: GridView
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        grid = findViewById(R.id.grid)
        val apps = loadApps()
        adapter = AppAdapter(apps)
        grid.adapter = adapter
        findViewById<TextView>(R.id.appsCount).text = "${apps.size} apps"
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val app = adapter.getItem(position)
            try {
                startActivity(
                    Intent()
                        .setComponent(app.component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "No se pudo abrir ${app.label}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = packageManager.queryIntentActivities(intent, 0)
        return infos.asSequence()
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(packageManager).toString(),
                    icon = it.loadIcon(packageManager),
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    data class AppEntry(
        val label: String,
        val icon: Drawable,
        val component: ComponentName
    )

    private inner class AppAdapter(
        private val apps: List<AppEntry>
    ) : BaseAdapter() {

        override fun getCount(): Int = apps.size

        override fun getItem(position: Int): AppEntry = apps[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val app = apps[position]
            view.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
            view.findViewById<TextView>(R.id.label).text = app.label
            return view
        }
    }
}
