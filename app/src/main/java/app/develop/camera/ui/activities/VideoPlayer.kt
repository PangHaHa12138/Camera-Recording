package app.develop.camera.ui.activities

import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import app.develop.camera.R
import app.develop.camera.databinding.VideoPlayerBinding
import app.develop.camera.util.getParcelableExtra
import kotlin.concurrent.thread


class VideoPlayer : AppCompatActivity() {

    companion object {
        const val TAG = "VideoPlayer"
        const val IN_SECURE_MODE = "isInSecureMode"
        const val VIDEO_URI = "videoUri"
    }

    private lateinit var binding: VideoPlayerBinding

    private fun setWindowLayoutInDisplayCutout(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val layoutParams = window.attributes
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = layoutParams
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setWindowLayoutInDisplayCutout(window)
        val intent = this.intent
        if (intent.getBooleanExtra(IN_SECURE_MODE, false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // Android 27 及以上，使用 setShowWhenLocked() 和 setTurnScreenOn()
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                // Android 27 以下，使用 WindowManager flags 来替代
                val window = window
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
        binding = VideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.let {
            it.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.appbar)))
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        val uri = getParcelableExtra<Uri>(intent, VIDEO_URI)!!

        val videoView = binding.videoPlayer

        val mediaController = object : MediaController(this) {
            override fun show() {
                super.show()
                supportActionBar?.show()
            }

            override fun hide() {
                super.hide()
                supportActionBar?.hide()
            }
        }

        thread {
            var hasAudio = true
            try {
                MediaMetadataRetriever().use {
                    it.setDataSource(this, uri)
                    hasAudio =
                        it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                }
            } catch (e: Exception) {
                Log.d(TAG, "", e)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mainExecutor.execute {
                    extracted(hasAudio, videoView, mediaController, uri)
                }
            } else {
                runOnUiThread {
                    extracted(hasAudio, videoView, mediaController, uri)
                }
            }
        }
    }

    private fun extracted(
        hasAudio: Boolean,
        videoView: VideoView,
        mediaController: MediaController,
        uri: Uri
    ) {
        val lifecycleState = lifecycle.currentState

        if (lifecycleState == Lifecycle.State.DESTROYED) {
            return
        }

        val audioFocus =
            if (hasAudio) AudioManager.AUDIOFOCUS_GAIN else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioManager.AUDIOFOCUS_NONE
            } else {
                0
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            videoView.setAudioFocusRequest(audioFocus)
        }

        videoView.setOnPreparedListener { _ ->
            videoView.setMediaController(mediaController)

            if (lifecycleState == Lifecycle.State.RESUMED) {
                videoView.start()
            }

            supportActionBar?.show()
            mediaController.show(0)
        }

        videoView.setVideoURI(uri)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.show()
    }
}
