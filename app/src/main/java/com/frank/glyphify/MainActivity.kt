package com.frank.glyphify

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.frank.glyphify.databinding.ActivityMainBinding
import android.os.Build
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private fun setComposerAppVersion(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        val sharedPref: SharedPreferences =
            this.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPref.edit()

        if (manufacturer.equals("Nothing", ignoreCase = true)) {
            if(model.equals("A063")) {
                editor.putString("appVersion", "Spacewar Glyph Composer")
                editor.apply()
            }
            else if(model.equals("A065")) {
                editor.putString("appVersion", "Pong Glyph Composer")
                editor.apply()
            }
        }

        return "0"
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
    }

}