package app.develop.camera.ui.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.develop.camera.ImageDownscaler
import app.develop.camera.R
import app.develop.camera.capturer.getVideoThumbnail
import app.develop.camera.databinding.ActivityMediaShowDetailBinding
import app.develop.camera.ui.ZoomableImageView2
import app.develop.camera.util.executeIfAlive
import java.io.InputStream
import java.util.concurrent.Executors
import kotlin.math.max


class MediaShowDetailActivity : AppCompatActivity() {

    val asyncImageLoader = Executors.newSingleThreadExecutor()

    lateinit var binding: ActivityMediaShowDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMediaShowDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = this.intent

        val uriString = intent.getStringExtra("MEDIA_URI") // 获取传递的 Uri 字符串
        val uri: Uri = uriString.let { Uri.parse(it) } // 将字符串转换回 Uri
        val type = intent.getStringExtra("MEDIA_TYPE")

        val mediaPreview: ZoomableImageView2 = binding.slidePreview

        val playButton: ImageView = binding.playButton

        mediaPreview.disableZooming()
        mediaPreview.setOnClickListener(null)
        mediaPreview.visibility = View.INVISIBLE
        mediaPreview.setImageBitmap(null)

        val placeholderText = binding.placeholderText.root

        playButton.visibility = View.GONE

        asyncImageLoader.executeIfAlive {
            val bitmap: Bitmap? = try {
                if (type == "VIDEO") {
                    getVideoThumbnail(this, uri)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // Android 9.0 (API 28) 及以上版本，使用 ImageDecoder
                        val source = ImageDecoder.createSource(contentResolver, uri)
                        ImageDecoder.decodeBitmap(source, ImageDownscaler)
                    } else {
                        // Android 9.0 以下版本，使用 BitmapFactory
                        val inputStream = contentResolver.openInputStream(uri)
                        decodeBitmapForLowerAPI(inputStream)
                    }
                }
            } catch (e: Exception) {
                null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mainExecutor.execute {
                    extracted(
                        bitmap,
                        placeholderText,
                        mediaPreview,
                        uri,
                        type,
                        playButton
                    )
                }
            } else {
                runOnUiThread {
                    extracted(
                        bitmap,
                        placeholderText,
                        mediaPreview,
                        uri,
                        type,
                        playButton
                    )
                }
            }
        }

    }

    private fun extracted(
        bitmap: Bitmap?,
        placeholderText: TextView,
        mediaPreview: ZoomableImageView2,
        uri: Uri,
        type: String?,
        playButton: ImageView
    ) {
        if (bitmap != null) {
            placeholderText.visibility = View.GONE
            mediaPreview.visibility = View.VISIBLE
            mediaPreview.setImageBitmap(bitmap)

            if (type == "VIDEO") {
                playButton.visibility = View.VISIBLE
            } else if (type == "IMAGE") {
                mediaPreview.enableZooming()
            }

            mediaPreview.setOnClickListener {

                if (type == "VIDEO") {
                    val intent = Intent(this, VideoPlayer::class.java)
                    intent.putExtra(VideoPlayer.VIDEO_URI, uri)
                    intent.putExtra(VideoPlayer.IN_SECURE_MODE, false)
                    startActivity(intent)
                }
            }
        } else {
            mediaPreview.visibility = View.INVISIBLE

            val resId = if (type == "IMAGE") {
                R.string.inaccessible_image
            } else {
                R.string.inaccessible_video
            }

            placeholderText.visibility = View.VISIBLE
            placeholderText.setText(getString(resId, uri.path))
        }
    }


    private fun decodeBitmapForLowerAPI(inputStream: InputStream?, maxSide: Int = 4500): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true // Get image dimensions without loading bitmap
            BitmapFactory.decodeStream(inputStream, null, this)
        }
        val w = options.outWidth
        val h = options.outHeight
        val largerSide = max(w, h)
        var inSampleSize = 1
        if (largerSide > maxSide) {
            val ratio = largerSide.toDouble() / maxSide
            inSampleSize = ratio.toInt() // Calculate sample size for downscaling
        }
        // Reopen the input stream since it was consumed in the previous step
        inputStream?.reset()
        options.inJustDecodeBounds = false // Load the actual bitmap
        options.inSampleSize = inSampleSize // Set sample size for downscaling
        return BitmapFactory.decodeStream(inputStream, null, options)
    }


}