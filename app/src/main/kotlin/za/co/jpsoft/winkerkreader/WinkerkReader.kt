package za.co.jpsoft.winkerkreader

import android.app.Application
import za.co.jpsoft.winkerkreader.utils.AppInitializer

class WinkerkReader : Application() {

    override fun onCreate() {
        super.onCreate()
        // One‑time app initialisation
        AppInitializer.initializeApp(this)
    }
}