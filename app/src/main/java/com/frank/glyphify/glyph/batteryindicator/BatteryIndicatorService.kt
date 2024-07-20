package com.frank.glyphify.glyph.batteryindicator

import android.app.Notification
import android.app.Service
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import com.frank.glyphify.Constants.CHANNEL_ID
import com.frank.glyphify.R
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphManager


class BatteryIndicatorService : Service() {
    private lateinit var mGM: GlyphManager
    private var mCallback: GlyphManager.Callback? = null

    private lateinit var sensorEventListener: SensorEventListener
    private var lastShakeTime: Long = 0

    // hold sensor values of the previous event
    var lastX = 0f
    var lastY = 0f
    var lastZ = 0f

    private fun getBatteryPercentage(context: Context): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return (level / scale.toFloat() * 100).toInt()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        init()

        val localGM = GlyphManager.getInstance(applicationContext)
        localGM?.init(mCallback)
        mGM = localGM

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorEventListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }

            override fun onSensorChanged(event: SensorEvent) {

                val shakeThreshold = 0.25

                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    // calculate the force of shake
                    val deltaX = x - lastX
                    val deltaY = y - lastY
                    val deltaZ = z - lastZ
                    val shakeForce = Math.abs(deltaX + deltaY + deltaZ)

                    lastX = x
                    lastY = y
                    lastZ = z

                    // check if the device is approximately horizontal
                    if ((Math.abs(z) - 9.8) < 1 && Math.abs(x) < 2 && Math.abs(y) < 2) {

                        val currentTime = System.currentTimeMillis()

                        if (shakeForce > shakeThreshold && currentTime - lastShakeTime > 3500) {
                            lastShakeTime = currentTime

                            val builder = mGM.glyphFrameBuilder
                            val batteryLevel = getBatteryPercentage(applicationContext)
                            val batteryIndicatorFrame = builder.buildChannel(Glyph.Code_23111.C_1).build()

                            // the progress value doesn't work as expected, rescaling the battery
                            // percentage to make the glyph progression make sense
                            mGM.displayProgressAndToggle(
                                batteryIndicatorFrame,
                                (batteryLevel * 0.8).toInt(),
                                false)

                            // all methods like buildPeriod etc don't seem to work, workaround
                            Thread.sleep(3000)
                            mGM.turnOff()
                        }
                    }
                }
            }
        }

        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onDestroy() {
        try {
            mGM.turnOff()
            mGM.closeSession()
        }
        catch (e: GlyphException) {
            Log.e(TAG, e.message!!)
        }
        mGM.unInit()

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(sensorEventListener)
        super.onDestroy()
    }

    private fun init() {
        mCallback = object : GlyphManager.Callback {
            override fun onServiceConnected(componentName: ComponentName) {
                if (Common.is20111()) mGM.register(Common.DEVICE_20111)
                if (Common.is22111()) mGM.register(Common.DEVICE_22111)
                if (Common.is23111()) mGM.register(Common.DEVICE_23111)

                try {
                    mGM.openSession()
                }
                catch (e: GlyphException) {
                    Log.e(TAG, e.message!!)
                }
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                mGM.closeSession()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(this.getString(R.string.app_name))
            .setContentText(this.getString(R.string.service_battery_indicator_title))
            .build()

        startForeground(42, notification)

        return START_STICKY
    }

}