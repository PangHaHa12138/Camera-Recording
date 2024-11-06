package app.develop.camera.capturer

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.app.NotificationCompat
import app.develop.camera.App
import app.develop.camera.CapturedItem
import app.develop.camera.R
import app.develop.camera.ui.activities.MainActivity
import app.develop.camera.ui.activities.SecureMainActivity
import app.develop.camera.util.printStackTraceToString

private const val imageFileFormat = ".jpg"
var isTakingPicture: Boolean = false

class ImageCapturer(val mActivity: MainActivity) {
    val camConfig = mActivity.camConfig

    private fun fadeCaptureButton() {
        mActivity.captureButton.isEnabled = false

        val fadeAnimator = ValueAnimator.ofFloat(1.0f, 0.6f)
        fadeAnimator.setDuration(100)
            .addUpdateListener { animation ->
                val alpha = animation.animatedValue as Float
                mActivity.captureCircle.alpha = alpha
            }
        val scaleAnimator = ValueAnimator.ofFloat(1.0f, 0.9f)
        scaleAnimator.setDuration(100)
            .addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                mActivity.captureCircle.scaleX = scale
                mActivity.captureCircle.scaleY = scale
            }
        fadeAnimator.start()
        scaleAnimator.start()

    }

    private fun unfadeCaptureButton() {
        mActivity.captureButton.isEnabled = true

        val fadeAnimator = ValueAnimator.ofFloat(0.6f, 1.0f)
        fadeAnimator.setDuration(100)
            .addUpdateListener { animation ->
                val alpha = animation.animatedValue as Float
                mActivity.captureCircle.alpha = alpha
            }
        val scaleAnimator = ValueAnimator.ofFloat(0.9f, 1.0f)
        scaleAnimator.setDuration(100)
            .addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                mActivity.captureCircle.scaleX = scale
                mActivity.captureCircle.scaleY = scale
            }
        scaleAnimator.start()
        fadeAnimator.start()
    }

    @SuppressLint("RestrictedApi")
    fun takePicture() {
        if (camConfig.camera == null) {
            return
        }

        if (!camConfig.canTakePicture) {
            mActivity.showMessage(R.string.unsupported_taking_picture_while_recording)
            return
        }

        if (isTakingPicture) {
            return
        }

        val imageMetadata = ImageCapture.Metadata()
        imageMetadata.isReversedHorizontal =
            camConfig.lensFacing == CameraSelector.LENS_FACING_FRONT
                    && camConfig.saveImageAsPreviewed

        if (camConfig.requireLocation) {
            val location = (mActivity.applicationContext as App).getLocation()
            if (location == null) {
                mActivity.showMessage(R.string.location_unavailable)
            } else {
                imageMetadata.location = location
            }
        }

        val preview = mActivity.imagePreview

        val imageCapture = camConfig.imageCapture!!

        val imageSaver = ImageSaver(
            this,
            mActivity.applicationContext,
            imageCapture.jpegQuality,
            camConfig.storageLocation,
            imageFileFormat,
            imageMetadata,
            camConfig.removeExifAfterCapture,
            targetThumbnailWidth = preview.width,
            targetThumbnailHeight = preview.height,
        )

        isTakingPicture = true

        imageCapture.takePicture(ImageSaver.imageCaptureCallbackExecutor, imageSaver)
        fadeCaptureButton()
    }

    fun onCaptureSuccess() {
        unfadeCaptureButton()
        isTakingPicture = false

        camConfig.mPlayer.playShutterSound()
        camConfig.snapPreview()

        mActivity.previewLoader.visibility = View.VISIBLE
        if (camConfig.selfIlluminate) {

            val animation: Animation = AlphaAnimation(0.8f, 0f)
            animation.duration = 200
            animation.interpolator = LinearInterpolator()
            animation.fillAfter = true

            mActivity.mainOverlay.setImageResource(android.R.color.white)

            animation.setAnimationListener(
                object : Animation.AnimationListener {
                    override fun onAnimationStart(p0: Animation?) {
                        mActivity.mainOverlay.visibility = View.VISIBLE
                    }

                    override fun onAnimationEnd(p0: Animation?) {
                        mActivity.mainOverlay.visibility = View.INVISIBLE
                        mActivity.mainOverlay.setImageResource(android.R.color.transparent)
                    }

                    override fun onAnimationRepeat(p0: Animation?) {}

                }
            )

            mActivity.mainOverlay.startAnimation(animation)
        }
    }

    fun onCaptureError(exception: ImageCaptureException) {
        Log.e(TAG, "onCaptureError", exception)

        unfadeCaptureButton()
        isTakingPicture = false
        mActivity.previewLoader.visibility = View.GONE

        if (mActivity.isStarted) {
            val msg = mActivity.getString(
                R.string.unable_to_capture_image_verbose,
                exception.imageCaptureError
            )
            showErrorDialog(msg, exception)
        }
    }

    fun onImageSaverSuccess(item: CapturedItem) {
        camConfig.updateLastCapturedItem(item)

        if (mActivity is SecureMainActivity) {
            mActivity.capturedItems.add(item)
        }
    }

    fun onStorageLocationNotFound() {
        camConfig.onStorageLocationNotFound()
    }

    fun onImageSaverError(exception: ImageSaverException, skipErrorDialog: Boolean) {
        Log.e(TAG, "onImageSaverError", exception)
        mActivity.previewLoader.visibility = View.GONE
        if (!mActivity.isStarted) {
            val notificationManager = mActivity.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "image_saver_error"
                val channel = NotificationChannel(
                    channelId, mActivity.getString(R.string.unable_to_save_image),
                    NotificationManager.IMPORTANCE_HIGH
                )

                val notif = Notification.Builder(mActivity, channelId).apply {
                    setSmallIcon(R.drawable.info)
                    setContentTitle(mActivity.getString(R.string.unable_to_save_image))
                }.build()

                mActivity.getSystemService(NotificationManager::class.java).let {
                    it.createNotificationChannel(channel)
                    it.notify(1, notif)
                }
            } else {
                // Android 26 以下版本，不需要 NotificationChannel
                val notif = NotificationCompat.Builder(mActivity, "default_channel_id").apply {
                    setSmallIcon(R.drawable.info)
                    setContentTitle(mActivity.getString(R.string.unable_to_save_image))
                    setPriority(NotificationCompat.PRIORITY_HIGH)
                }.build()

                // 显示通知
                notificationManager?.notify(1, notif)
            }

            return
        }

        if (skipErrorDialog) {
            mActivity.showMessage(R.string.unable_to_save_image)
        } else {
            val msg =
                mActivity.getString(R.string.unable_to_save_image_verbose, exception.place.name)
            showErrorDialog(msg, exception)
        }
    }

    private fun showErrorDialog(message: String, exception: Throwable) {
        val ctx = mActivity

        AlertDialog.Builder(ctx).apply {
            setMessage(message)
            setPositiveButton(R.string.show_details) { _, _ ->
                val pkgName = ctx.packageName
                val pkgVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ctx.packageManager.getPackageInfo(pkgName, 0).longVersionCode
                } else {
                    ctx.packageManager.getPackageInfo(pkgName, 0).versionCode.toLong()
                }
                val text = "osVersion: ${Build.FINGERPRINT}" +
                        "\npackage: $pkgName:$pkgVersion" +
                        "\n\n${exception.printStackTraceToString()}"

                AlertDialog.Builder(ctx).apply {
                    setItems(text.lines().toTypedArray(), null)
                    setNeutralButton(R.string.copy_to_clipboard) { _, _ ->
                        val clipData = ClipData.newPlainText(exception.javaClass.name, text)
                        val cm = mActivity.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(clipData)
                        ctx.showMessage(R.string.copied_text_to_clipboard)
                    }
                    show()
                }
            }
            show()
        }
    }

    fun onThumbnailGenerated(thumbnail: Bitmap) {
        mActivity.previewLoader.visibility = View.GONE
        mActivity.imagePreview.setImageBitmap(thumbnail)
    }

    companion object {
        private const val TAG = "ImageCapturer"
    }
}
