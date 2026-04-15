package za.co.jpsoft.winkerkreader.services


import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.widget.Toast
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO

class MyService : Service() {

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        Toast.makeText(
            baseContext,
            "Application Is Running in Background",
            Toast.LENGTH_SHORT
        ).show()

        val settings = getSharedPreferences(PREFS_USER_INFO, 0)
        val editor = settings.edit()

        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        val phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        editor.putString("CallerNumber", incomingNumber ?: "Unknown").apply()
                        startService(Intent(applicationContext, OproepDetailService::class.java))
                    }
                    else -> {
                        if (OproepDetailService.isOn) {
                            Handler().postDelayed({
                                editor.putString("CallerNumber", "XXXXXXXXXX").apply()
                                stopService(Intent(applicationContext, OproepDetailService::class.java))
                            }, 10000)
                        }
                    }
                }
            }
        }

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }
}