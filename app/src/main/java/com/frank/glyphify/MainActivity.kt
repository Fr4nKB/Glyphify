package com.frank.glyphify

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.frank.glyphify.databinding.ActivityMainBinding
import com.frank.glyphify.ui.dialogs.Dialog
import com.google.android.material.bottomnavigation.BottomNavigationView
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

    private fun supportMe() {
        Dialog.showDialog(
            this,
            R.layout.first_boot,
            mapOf(
                R.id.paypalBtn to {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.paypal.com/donate/?hosted_button_id=HJU8Y7F34Z6TL")))
                                  },
                R.id.githubBtn to {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Fr4nKB/Glyphify")))
                                  },
                R.id.negativeButton to {}
            ),
            isCancelable = false,
            delayEnableButtonId = R.id.negativeButton,
            delayMillis = 10000
        )
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

        val sharedPref: SharedPreferences =
            this.getSharedPreferences("settings", Context.MODE_PRIVATE)

        val isFirstBoot = sharedPref.getBoolean("firstboot", true)
        val randomNumber = Random.nextInt(1, 11)

        if (isFirstBoot || randomNumber == 1) {
            supportMe()
            if (isFirstBoot) {
                val editor: SharedPreferences.Editor = sharedPref.edit()
                editor.putBoolean("firstboot", false)
                editor.apply()
            }
        }

        findViewById<ImageButton>(R.id.btn_donate).setOnClickListener() {
            startActivity(
                Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.paypal.com/donate/?hosted_button_id=HJU8Y7F34Z6TL"))
            )
        }

    }
}