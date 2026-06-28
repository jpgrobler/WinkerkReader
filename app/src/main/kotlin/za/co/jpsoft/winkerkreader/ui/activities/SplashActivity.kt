package za.co.jpsoft.winkerkreader.ui.activities

// SplashActivity.kt


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.utils.MainNavigationController

/**
 * Created by Pieter Grobler on 30/08/2017.
 */
class SplashActivity : AppCompatActivity() {
    private val navigationController by lazy { MainNavigationController(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //startActivity(Intent(this, MainActivity::class.java))
        navigationController.navigateToMain()
        finish()
    }
}