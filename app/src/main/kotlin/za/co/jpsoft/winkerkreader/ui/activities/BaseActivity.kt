package za.co.jpsoft.winkerkreader.ui.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal base for all Activities.
 * - Applies edge‑to‑edge insets.
 * - Provides no navigation or authentication logic.
 * Activities that need app‑lock should extend [AuthBaseActivity].
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called BEFORE setContentView()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }
}