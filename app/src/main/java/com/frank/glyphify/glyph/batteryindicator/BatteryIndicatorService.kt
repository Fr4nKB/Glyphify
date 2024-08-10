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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.frank.glyphify.Constants.CHANNEL_ID
import com.frank.glyphify.R
import com.frank.glyphify.glyph.extendedessential.ExtendedEssentialService
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class BatteryIndicatorService : Service() {
    private var mGM: GlyphManager? = null
    private var mCallback: GlyphManager.Callback? = null
    private lateinit var sensorEventListener: SensorEventListener
    private var lastShakeTime: Long = 0
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var powerConnectionReceiver: PowerConnectionReceiver

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

    private fun signalEE() {
        val serviceIntent = Intent(this, ExtendedEssentialService::class.java)
        serviceIntent.action = "SHOW_GLYPHS"
        startService(serviceIntent)
    }

    private fun registerSensorListener() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorEventListener = object : SensorEventListener {
            val shakeThreshold = 0.25

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }

            override fun onSensorChanged(event: SensorEvent) {
                if(event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
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

                    // check if the device is approximately horizontal and facing down
                    if(z < -9.8 && (Math.abs(z) - 9.8) < 1 && Math.abs(x) < 2 && Math.abs(y) < 2) {

                        val currentTime = System.currentTimeMillis()

                        if(shakeForce > shakeThreshold && currentTime - lastShakeTime > 3500) {
                            lastShakeTime = currentTime

                            val builder = mGM!!.glyphFrameBuilder
                            val batteryLevel = getBatteryPercentage(applicationContext)
                            val batteryIndicatorFrame = builder.buildChannel(Glyph.Code_23111.C_1).build()

                            serviceScope.launch {
                                try {
                                    val wakeLockTime = 10 * 1000

                                    if(!wakeLock.isHeld) wakeLock.acquire(wakeLockTime.toLong())

                                    mGM?.openSession()
                                    for(i in 0..batteryLevel step 10) {
                                        mGM?.displayProgressAndToggle(
                                            batteryIndicatorFrame,
                                            i,
                                            false)

                                        delay(30)
                                    }

                                    delay(3000)
                                    mGM?.turnOff()
                                    signalEE()
                                }
                                finally {
                                    if(wakeLock.isHeld) wakeLock.release()
                                    mGM?.closeSession()
                                }
                            }

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

    private fun unregisterSensorListener() {
        try {
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorManager.unregisterListener(sensorEventListener)
        }
        catch (_: Exception) {}
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        Handler(Looper.getMainLooper()).postDelayed({
            init()
            val localGM = GlyphManager.getInstance(applicationContext)
            localGM?.init(mCallback)
            mGM = localGM

            // turn off glyphs in case some of them were stuck
            mGM?.openSession()
            mGM?.turnOff()
            mGM?.closeSession()
        }, 3 * 1000)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Glyhpify::BatteryIndicator"
        )

        // use this service to register Phone(2a)'s battery indicator
        powerConnectionReceiver = PowerConnectionReceiver()
        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerConnectionReceiver, powerFilter)
    }

    override fun onDestroy() {
        try {
            mGM?.turnOff()
            mGM?.closeSession()
        }
        catch (e: GlyphException) {
            Log.e(TAG, e.message!!)
        }
        mGM?.unInit()

        unregisterReceiver(powerConnectionReceiver)
        unregisterSensorListener()

        super.onDestroy()
    }

    private fun init() {
        mCallback = object : GlyphManager.Callback {
            override fun onServiceConnected(componentName: ComponentName) {
                if (Common.is20111()) mGM?.register(Common.DEVICE_20111)
                if (Common.is22111()) mGM?.register(Common.DEVICE_22111)
                if (Common.is23111()) mGM?.register(Common.DEVICE_23111)
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                mGM?.turnOff()
                mGM?.closeSession()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if(intent != null) {
            val action = intent.action
            if(action == "POWER_ON") {
                registerSensorListener()
            }
            else if(action == "POWER_OFF") {
                mGM?.turnOff()
                mGM?.closeSession()
                unregisterSensorListener()
                signalEE()
            }
            else {
                val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(this.getString(R.string.title_foreground_notification))
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_sunlight)
                    .build()

                startForeground(2, notification)
            }
        }

        return START_STICKY
    }

}