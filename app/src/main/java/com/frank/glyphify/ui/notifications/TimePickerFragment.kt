package com.frank.glyphify.ui.notifications

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import com.frank.glyphify.R
import com.frank.glyphify.Util.exactAlarm
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class TimePickerFragment(private val context: Context, private val key: String, private val btn: MaterialButton):
    DialogFragment(), TimePickerDialog.OnTimeSetListener {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use the current time as the default values for the picker.
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)

        // Create a new instance of TimePickerDialog and return it.
        return TimePickerDialog(activity, TimePickerDialog.THEME_DEVICE_DEFAULT_DARK,
            this, hour, minute, DateFormat.is24HourFormat(activity))
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        val sharedPref: SharedPreferences =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        val time = "$hourOfDay:$minute"
        val editor: SharedPreferences.Editor = sharedPref.edit()
        editor.putString(key, time)
        editor.apply()

        if(key == "sleepStart") {
            btn.text = getString(R.string.btn_sleep_mode_start) + ": " + time
            exactAlarm(context, "SLEEP_ON", 1)
        }
        else if(key == "sleepEnd") {
            btn.text = getString(R.string.btn_sleep_mode_end) + ": " + time
            exactAlarm(context, "SLEEP_OFF", 1)
        }
    }
}
