package za.co.jpsoft.winkerkreader.ui.activities

// SplashActivity.kt


import android.os.Bundle
import za.co.jpsoft.winkerkreader.utils.ui.MainNavigationController

/**
 * Created by Pieter Grobler on 30/08/2017.
 */
class SplashActivity : BaseActivity() {
    private val navigationController by lazy { MainNavigationController(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //startActivity(Intent(this, MainActivity::class.java))
        navigationController.navigateToMain()
        finish()
    }
}