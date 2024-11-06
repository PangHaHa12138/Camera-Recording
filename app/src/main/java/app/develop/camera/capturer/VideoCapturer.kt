package app.develop.camera.capturer

import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore.MediaColumns
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import app.develop.camera.App
import app.develop.camera.CamConfig
import app.develop.camera.CapturedItem
import app.develop.camera.ITEM_TYPE_VIDEO
import app.develop.camera.R
import app.develop.camera.VIDEO_NAME_PREFIX
import app.develop.camera.ui.activities.MainActivity
import app.develop.camera.ui.activities.SecureMainActivity
import app.develop.camera.ui.activities.VideoCaptureActivity
import app.develop.camera.util.asExecutor
import app.develop.camera.util.getTreeDocumentUri
import app.develop.camera.util.removePendingFlagFromUri
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoCapturer(private val mActivity: MainActivity) {

    val camConfig = mActivity.camConfig

    var isRecording = false

    private val videoFileFormat = ".mp4"

    private var recording: Recording? = null

    var includeAudio: Boolean = false

    var isPaused = false
        set(value) {
            if (isRecording) {
                if (value) {
                    recording?.pause()
                    mActivity.flipCamIcon.setImageResource(R.drawable.play)
                } else {
                    recording?.resume()
                    mActivity.flipCamIcon.setImageResource(R.drawable.pause)
                }
            }
            field = value
        }

    private val handler = Handler(Looper.getMainLooper())

    private fun updateTimerTime(timeInNanos: Long) {
        val timeInSec = timeInNanos / (1000 * 1000 * 1000)
        val sec = timeInSec % 60
        val min = timeInSec / 60 % 60
        val hour = timeInSec / 3600

        val timerText: String = if (hour == 0L) {
            String.format(Locale.ROOT, "%02d:%02d", min, sec)
        } else {
            String.format(Locale.ROOT, "%02d:%02d:%02d", hour, min, sec)
        }

        mActivity.timerView.text = timerText
    }

    private class RecordingContext(
        val pendingRecording: PendingRecording,
        val uri: Uri,
        val fileDescriptor: ParcelFileDescriptor?,
        val outputStream: OutputStream?,
        val shouldAddToGallery: Boolean,
        val isPendingMediaStoreUri: Boolean,
    )

    private fun createRecordingContext(recorder: Recorder, fileName: String): RecordingContext? {
        val mimeType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(videoFileFormat) ?: "video/mp4"

        val ctx = mActivity
        val contentResolver = ctx.contentResolver

        val uri: Uri?
        var shouldAddToGallery = true
        var isPendingMediaStoreUri = false

        if (ctx is VideoCaptureActivity && ctx.isOutputUriAvailable()) {
            uri = ctx.outputUri
            shouldAddToGallery = false
        } else {
            val storageLocation = camConfig.storageLocation

            if (storageLocation == CamConfig.SettingValues.Default.STORAGE_LOCATION) {
                val contentValues = ContentValues().apply {
                    put(MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaColumns.MIME_TYPE, mimeType)
                    put(MediaColumns.RELATIVE_PATH, DEFAULT_MEDIA_STORE_CAPTURE_PATH)
                    put(MediaColumns.IS_PENDING, 1)
                }
                uri = contentResolver.insert(CamConfig.videoCollectionUri, contentValues)
                isPendingMediaStoreUri = true
            } else {
                val treeUri = Uri.parse(storageLocation)
                val treeDocumentUri = getTreeDocumentUri(treeUri)

                uri = DocumentsContract.createDocument(
                    contentResolver,
                    treeDocumentUri,
                    mimeType,
                    fileName
                )
            }
        }

        if (uri == null) {
            return null
        }

        var location: Location? = null
        if (camConfig.requireLocation) {
            location = (mActivity.applicationContext as App).getLocation()
            if (location == null) {
                mActivity.showMessage(R.string.location_unavailable)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 26 (Oreo) 及以上版本
            contentResolver.openFileDescriptor(uri, "w")?.let {
                val outputOptions = FileDescriptorOutputOptions.Builder(it)
                    .setLocation(location)
                    .build()
                val pendingRecording = recorder.prepareRecording(ctx, outputOptions)
                return RecordingContext(
                    pendingRecording,
                    uri,
                    it,
                    null,
                    shouldAddToGallery,
                    isPendingMediaStoreUri
                )
            }
        } else {
            // Android 26 以下版本，使用 FileOutputStream 进行文件处理
            try {
                contentResolver.openOutputStream(uri)?.let { outputStream ->
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        val outputOptions = FileOutputOptions.Builder(file)
                            .setLocation(location)
                            .build()
                        val pendingRecording = recorder.prepareRecording(ctx, outputOptions)
                        return RecordingContext(
                            pendingRecording,
                            uri,
                            null,
                            outputStream,
                            shouldAddToGallery,
                            isPendingMediaStoreUri
                        )
                    } else {
                        // Handle error case where file does not exist
                        throw IOException("File does not exist for the given URI.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    fun startRecording() {
        Log.d("MyTag","===> startRecording")
        if (camConfig.camera == null) return
        val recorder = camConfig.videoCapture?.output ?: return

        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = VIDEO_NAME_PREFIX + dateString + videoFileFormat

        includeAudio = false

        val ctx = mActivity

        if (ctx.settingsDialog.includeAudioToggle.isChecked) {
            if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED) {
                includeAudio = true
            } else {
                ctx.restartRecordingWithMicPermission()
                return
            }
        }

        val recordingCtx = try {
            createRecordingContext(recorder, fileName)!!
        } catch (exception: Exception) {
            val foreignUri = ctx is VideoCaptureActivity && ctx.isOutputUriAvailable()
            if (!foreignUri) {
                camConfig.onStorageLocationNotFound()
            }
            ctx.showMessage(R.string.unable_to_access_output_file)
            return
        }

        val pendingRecording = recordingCtx.pendingRecording

        if (includeAudio) {
            pendingRecording.withAudioEnabled()
        }

        beforeRecordingStarts()

        isRecording = true

        camConfig.mPlayer.playVRStartSound(handler) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                recording = pendingRecording.start(ctx.mainExecutor) { event ->
                    extracted(event, ctx, recordingCtx, dateString)
                }
            } else {
                val mainHandler = Handler(Looper.getMainLooper())
                recording = pendingRecording.start(mainHandler.asExecutor()) { event ->
                    extracted(event, ctx, recordingCtx, dateString)
                }
            }

            try {
                // FileDescriptorOutputOptions doc says that the file descriptor should be closed by the
                // caller, and that it's safe to do so as soon as pendingRecording.start() returns
                recordingCtx.fileDescriptor?.close()
                recordingCtx.outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun extracted(
        event: VideoRecordEvent?,
        ctx: MainActivity,
        recordingCtx: RecordingContext,
        dateString: String
    ) {
        if (event is VideoRecordEvent.Start) {
            onRecordingStart()
        }

        if (event is VideoRecordEvent.Status) {
            updateTimerTime(event.recordingStats.recordedDurationNanos)
        }

        if (event is VideoRecordEvent.Finalize) {
            afterRecordingStops()

            camConfig.mPlayer.playVRStopSound()

            if (event.hasError()) {
                when (event.error) {
                    VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> {
                        ctx.showMessage(R.string.recording_too_short_to_be_saved)
                        return
                    }

                    VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
                    VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR,
                    VideoRecordEvent.Finalize.ERROR_UNKNOWN -> {
                        ctx.showMessage(
                            ctx.getString(
                                R.string.unable_to_save_video_verbose,
                                event.error
                            )
                        )
                        return
                    }

                    else -> {
                        ctx.showMessage(ctx.getString(R.string.error_during_recording, event.error))
                    }
                }
            }

            val uri = recordingCtx.uri

            if (recordingCtx.isPendingMediaStoreUri) {
                try {
                    removePendingFlagFromUri(ctx.contentResolver, uri)
                } catch (e: Exception) {
                    ctx.showMessage(R.string.unable_to_save_video)
                }
            }

            if (recordingCtx.shouldAddToGallery) {
                val item = CapturedItem(ITEM_TYPE_VIDEO, dateString, uri)
                camConfig.updateLastCapturedItem(item)

                ctx.updateThumbnail()

                if (ctx is SecureMainActivity) {
                    ctx.capturedItems.add(item)
                }
            }

            if (ctx is VideoCaptureActivity) {
                ctx.afterRecording(uri)
            }
        }
    }

    private val dp32 = 32 * mActivity.resources.displayMetrics.density
    private val dp12 = 12 * mActivity.resources.displayMetrics.density

    private fun beforeRecordingStarts() {
        mActivity.previewView.keepScreenOn = true
    }

    private fun onRecordingStart() {
        // TODO: Uncomment this once the main indicator UI gets implemented
        // mActivity.micOffIcon.visibility = View.GONE

        val gd: GradientDrawable = mActivity.captureCircle.drawable as GradientDrawable
        val animator = ValueAnimator.ofFloat(dp32, dp12)
        val scaleAnimator = ValueAnimator.ofFloat(1.0f, 0.5f)
        scaleAnimator.setDuration(300)
            .addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            mActivity.captureCircle.scaleX = scale
            mActivity.captureCircle.scaleY = scale
        }
        animator.setDuration(300)
            .addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                gd.cornerRadius = value
            }
        animator.start()
        scaleAnimator.start()

        mActivity.settingsDialog.videoQualitySpinner.isEnabled = false
        mActivity.settingsDialog.enableEISToggle.isEnabled = false

        mActivity.flipCamIcon.setImageResource(R.drawable.pause)
        isPaused = false
        mActivity.cancelButtonView.visibility = View.GONE

        if (mActivity.requiresVideoModeOnly) {
            mActivity.thirdOption.visibility = View.INVISIBLE
        }

        mActivity.settingsDialog.lRadio.isEnabled = false
        mActivity.settingsDialog.qRadio.isEnabled = false

        mActivity.thirdCircle.setImageResource(R.drawable.camera_shutter)
        mActivity.tabLayout.visibility = View.INVISIBLE
        mActivity.timerView.setText(R.string.start_value_timer)
        mActivity.timerView.visibility = View.VISIBLE
    }

    private fun afterRecordingStops() {

        val gd: GradientDrawable = mActivity.captureCircle.drawable as GradientDrawable
        val animator = ValueAnimator.ofFloat(dp12, dp32)
        val scaleAnimator = ValueAnimator.ofFloat(0.5f, 1.0f)
        scaleAnimator.setDuration(300)
            .addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            mActivity.captureCircle.scaleX = scale
            mActivity.captureCircle.scaleY = scale
        }
        animator.setDuration(300)
            .addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                gd.cornerRadius = value
            }
        animator.start()
        scaleAnimator.start()

        mActivity.timerView.visibility = View.GONE
        mActivity.flipCamIcon.setImageResource(R.drawable.flip_camera)

        mActivity.settingsDialog.videoQualitySpinner.isEnabled = true
        mActivity.settingsDialog.enableEISToggle.isEnabled = true

        if (mActivity !is VideoCaptureActivity) {
            mActivity.thirdOption.visibility = View.VISIBLE
        }

        if (!mActivity.requiresVideoModeOnly) {
            mActivity.settingsDialog.lRadio.isEnabled = true
            mActivity.settingsDialog.qRadio.isEnabled = true
        }

        if (mActivity !is VideoCaptureActivity) {
            mActivity.thirdCircle.setImageResource(R.drawable.option_circle)
            mActivity.cancelButtonView.visibility = View.VISIBLE
            mActivity.tabLayout.visibility = View.VISIBLE
        }

        mActivity.previewView.keepScreenOn = false

        // TODO: Uncomment this once the main indicator UI gets implemented
        // if (!mActivity.config.includeAudio)
        //   mActivity.micOffIcon.visibility = View.VISIBLE

        //isRecording = false

        mActivity.forceUpdateOrientationSensor()
    }

    fun muteRecording() {
        recording?.mute(true)
    }

    fun unmuteRecording() {
        recording?.mute(false)
    }

    fun stopRecording() {
        Log.d("MyTag","===> stopRecording")
        isRecording = false
        recording?.stop()
        recording?.close()
        recording = null
    }
}

@Throws(Exception::class)
fun getVideoThumbnail(context: Context, uri: Uri?): Bitmap? {
    MediaMetadataRetriever().use {
        it.setDataSource(context, uri)
        return it.frameAtTime
    }
}
