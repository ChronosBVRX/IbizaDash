package com.chronosbvrx.ibizadash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chronosbvrx.ibizadash.map.MapHelper
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale
import kotlin.math.roundToInt

class MapActivity : AppCompatActivity(), LocationListener {

    private lateinit var map: MapView
    private lateinit var speed: TextView
    private lateinit var locationManager: LocationManager
    private var overlay: MyLocationNewOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        map = findViewById(R.id.map)
        speed = findViewById(R.id.speed)
        MapHelper.configure(this, map)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        requestLocation()
    }

    private fun requestLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                101
            )
        }
    }

    private fun enableLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (overlay == null) {
            overlay = MapHelper.addLocation(this, map)
        } else {
            overlay?.enableMyLocation()
        }
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider != null) {
            locationManager.requestLocationUpdates(provider, 1000L, 0f, this)
            locationManager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        }
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            val kmh = location.speed * 3.6f
            speed.text = String.format(Locale.getDefault(), "%.0f km/h", kmh)
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    override fun onResume() {
        super.onResume()
        map.onResume()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(this)
        }
        overlay?.disableMyLocation()
    }

    override fun onDestroy() {
        map.onDetach()
        super.onDestroy()
    }
}
