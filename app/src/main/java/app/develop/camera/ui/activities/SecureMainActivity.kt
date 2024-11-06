package app.develop.camera.ui.activities

import android.content.SharedPreferences
import app.develop.camera.CapturedItem
import app.develop.camera.util.EphemeralSharedPrefsNamespace
import app.develop.camera.util.getPrefs

open class SecureMainActivity : MainActivity(), SecureActivity {
    val capturedItems = ArrayList<CapturedItem>()
    val ephemeralPrefsNamespace = EphemeralSharedPrefsNamespace()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return ephemeralPrefsNamespace.getPrefs(this, name, mode, cloneOriginal = true)
    }
}
