// SplashActivity.kt
package za.co.jpsoft.winkerkreader

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Created by Pieter Grobler on 30/08/2017.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity2::class.java))
        finish()
    }
}