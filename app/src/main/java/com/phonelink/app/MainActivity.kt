package com.phonelink.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Hub.attach(applicationContext)
        ContextCompat.startForegroundService(this, Intent(this, LinkService::class.java))
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                PhoneLinkApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Hub.uiVisible = true
        Hub.refreshIps()
    }

    override fun onStop() {
        Hub.uiVisible = false
        super.onStop()
    }
}
