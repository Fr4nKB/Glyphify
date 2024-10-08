package com.frank.glyphify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.frank.glyphify.Constants.CHANNEL_ID
import com.frank.glyphify.Constants.PHONE1_MODEL_ID
import com.frank.glyphify.Constants.PHONE2A_MODEL_ID
import com.frank.glyphify.Constants.PHONE2_MODEL_ID
import com.frank.glyphify.databinding.ActivityMainBinding
import com.frank.glyphify.glyph.batteryindicator.BatteryIndicatorService
import com.frank.glyphify.glyph.visualizer.GlyphVisualizer
import com.frank.glyphify.ui.dialogs.Dialog.supportMe
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import kotlin.random.Random


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private fun setComposerAppVersion(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        val sharedPref: SharedPreferences =
            this.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPref.edit()

        if(manufacturer.equals("Nothing", ignoreCase = true)) {
            if(model.equals(PHONE1_MODEL_ID)) {
                editor.putString("appVersion", "v1-Spacewar Glyph Composer")
                editor.apply()
            }
            else if(PHONE2_MODEL_ID.contains(model)) {
                editor.putString("appVersion", "v1-Pong Glyph Composer")
                editor.apply()
            }
            else if(model.equals(PHONE2A_MODEL_ID)) {
                editor.putString("appVersion", "v1-Pacman Glyph Composer")
                editor.apply()
            }
        }

        return "0"
    }

    private fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_ID, importance)

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun welcomeMsg() {
        val sharedPref: SharedPreferences =
            this.getSharedPreferences("settings", Context.MODE_PRIVATE)

        val isFirstBoot = sharedPref.getBoolean("firstboot", true)
        val randomNumber = Random.nextInt(1, 11)

        val permHandler = PermissionHandling(this)

        if(isFirstBoot || randomNumber == 1) {
            supportMe(this, permHandler)
            if (isFirstBoot) {
                val editor: SharedPreferences.Editor = sharedPref.edit()
                editor.putBoolean("firstboot", false)
                editor.apply()
            }
        }
        else {
            val permissions = mutableListOf(
                Manifest.permission.POST_NOTIFICATIONS
            )
            permHandler.askRequiredPermissions(permissions, R.layout.dialog_perm_notifications)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.nav_view)
        bottomNavigationView.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
        bottomNavigationView.itemBackground = ColorDrawable(Color.TRANSPARENT)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)

        setComposerAppVersion()
        createNotificationChannel()

        welcomeMsg()

        if(Build.MODEL == PHONE2A_MODEL_ID) {
            val intent = Intent(this, BatteryIndicatorService::class.java)
            startService(intent)
        }
    }
}