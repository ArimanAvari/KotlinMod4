package com.example.kotlinmod4

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var statusText: TextView
    private lateinit var coordinatesText: TextView
    private lateinit var addressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var getAddressButton: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            loadCurrentAddress()
        } else {
            showError("Разрешение на геолокацию не выдано")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        locationClient = LocationServices.getFusedLocationProviderClient(this)
        statusText = findViewById(R.id.statusText)
        coordinatesText = findViewById(R.id.coordinatesText)
        addressText = findViewById(R.id.addressText)
        progressBar = findViewById(R.id.progressBar)
        getAddressButton = findViewById(R.id.getAddressButton)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        getAddressButton.setOnClickListener {
            requestLocationFlow()
        }
    }

    private fun requestLocationFlow() {
        if (!hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
            return
        }

        loadCurrentAddress()
    }

    private fun loadCurrentAddress() {
        if (!isLocationEnabled()) {
            showError("Геолокация отключена. Включи GPS или сетевое определение местоположения")
            return
        }

        progressBar.visibility = View.VISIBLE
        getAddressButton.isEnabled = false
        statusText.text = "Получаем координаты..."
        coordinatesText.text = ""
        addressText.text = ""

        screenScope.launch {
            try {
                val location = fetchCurrentLocation()
                if (location == null) {
                    showError("Не удалось получить текущие координаты")
                    return@launch
                }

                val latitude = location.latitude
                val longitude = location.longitude
                coordinatesText.text = "lat: %.5f, lng: %.5f".format(latitude, longitude)
                statusText.text = "Преобразуем координаты в адрес..."

                val address = reverseGeocode(latitude, longitude)
                if (address.isNullOrBlank()) {
                    showError("Координаты получили, но адрес определить не удалось")
                    return@launch
                }

                progressBar.visibility = View.INVISIBLE
                getAddressButton.isEnabled = true
                statusText.text = "Адрес найден"
                addressText.text = address
            } catch (e: Exception) {
                showError(e.message ?: "Не удалось получить адрес")
            }
        }
    }

    private suspend fun fetchCurrentLocation() = suspendCancellableCoroutine<Location?> { continuation ->
        val tokenSource = CancellationTokenSource()

        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
            .addOnSuccessListener { location ->
                continuation.resume(location)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }

        continuation.invokeOnCancellation {
            tokenSource.cancel()
        }
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            val geocoder = Geocoder(this@MainActivity, Locale("ru"))
            if (!Geocoder.isPresent()) {
                return@withContext null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        val line = addresses.firstOrNull()?.toReadableAddress()
                        continuation.resume(line)
                    }
                }
            } else {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)
                        ?.firstOrNull()
                        ?.toReadableAddress()
                } catch (_: IOException) {
                    null
                }
            }
        }
    }

    private fun Address.toReadableAddress(): String {
        val parts = listOfNotNull(
            thoroughfare,
            subThoroughfare,
            locality,
            adminArea,
            countryName
        )
        return parts.joinToString(", ")
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(LocationManager::class.java)
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showError(message: String) {
        progressBar.visibility = View.INVISIBLE
        getAddressButton.isEnabled = true
        statusText.text = "Ошибка"
        addressText.text = message
    }

    override fun onDestroy() {
        screenScope.cancel()
        super.onDestroy()
    }
}
