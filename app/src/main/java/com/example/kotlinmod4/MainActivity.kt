package com.example.kotlinmod4

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var numberText: TextView
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button

    private var randomService: RandomNumberService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RandomNumberService.RandomBinder
            randomService = binder.getService()
            randomService?.setOnNumberChangedListener { value ->
                runOnUiThread {
                    numberText.text = value.toString()
                }
            }
            isBound = true
            updateButtons()
            statusText.text = "Сервис подключён"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            randomService?.setOnNumberChangedListener(null)
            randomService = null
            isBound = false
            updateButtons()
            statusText.text = "Сервис отключён"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        numberText = findViewById(R.id.numberText)
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        connectButton.setOnClickListener {
            val intent = Intent(this, RandomNumberService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        disconnectButton.setOnClickListener {
            disconnectFromService()
        }

        updateButtons()
    }

    override fun onDestroy() {
        disconnectFromService()
        super.onDestroy()
    }

    private fun disconnectFromService() {
        if (!isBound) return

        randomService?.setOnNumberChangedListener(null)
        unbindService(serviceConnection)
        randomService = null
        isBound = false
        updateButtons()
        statusText.text = "Сервис отключён"
    }

    private fun updateButtons() {
        connectButton.isEnabled = !isBound
        disconnectButton.isEnabled = isBound
    }
}
