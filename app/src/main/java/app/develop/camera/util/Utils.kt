package app.develop.camera.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.DisplayMetrics
import app.develop.camera.CamConfig
import app.develop.camera.R
import app.develop.camera.capturer.DEFAULT_MEDIA_STORE_CAPTURE_PATH
import app.develop.camera.capturer.SAF_URI_HOST_EXTERNAL_STORAGE
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import kotlin.math.sqrt

fun Throwable.printStackTraceToString(): String {
    val baos = ByteArrayOutputStream(1000)
    this.printStackTrace(PrintStream(baos));
    return baos.toString()
}

fun getTreeDocumentUri(treeUri: Uri): Uri {
    val treeId = DocumentsContract.getTreeDocumentId(treeUri)
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
}

fun ExecutorService.executeIfAlive(r: Runnable) {
    try {
        execute(r)
    } catch (ignored: RejectedExecutionException) {
        check(this.isShutdown)
    }
}

fun storageLocationToUiString(ctx: Context, sl: String): String {
    if (sl == CamConfig.SettingValues.Default.STORAGE_LOCATION) {
        return "${ctx.getString(R.string.main_storage)}/$DEFAULT_MEDIA_STORE_CAPTURE_PATH"
    }

    val uri = Uri.parse(sl)
    val indexOfId = if (DocumentsContract.isDocumentUri(ctx, uri)) 3 else 1
    val locationId = uri.pathSegments[indexOfId]

    if (uri.host == SAF_URI_HOST_EXTERNAL_STORAGE) {
        val endOfVolumeId = locationId.lastIndexOf(':')
        val volumeId = locationId.substring(0, endOfVolumeId)

        val volumeName = if (volumeId == "primary") {
            ctx.getString(R.string.main_storage)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android N 及以上版本
                val sm = ctx.getSystemService(StorageManager::class.java)
                sm.storageVolumes.find {
                    volumeId == it.uuid
                }?.getDescription(ctx) ?: volumeId
            } else {
                // Android 6.0 及以下版本
                // 这里使用外部存储的路径作为替代方案，你可以根据你的需求进行更复杂的适配
                val externalStorage = Environment.getExternalStorageDirectory()
                externalStorage.absolutePath ?: volumeId
            }
        }

        val path = locationId.substring(endOfVolumeId + 1)

        return "$volumeName/$path"
    }

    try {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            uri,
            DocumentsContract.getTreeDocumentId(uri)
        )

        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.contentResolver.query(docUri, projection, null, null)?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } else {
            try {
                ctx.contentResolver.query(docUri, projection, null, null, null)?.use {
                    if (it.moveToFirst()) {
                        return it.getString(0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    } catch (ignored: Exception) {
    }

    return locationId
}


fun getScreenSizeInInches(displayMetrics: DisplayMetrics): Double {
    // 屏幕分辨率
    val widthPixels = displayMetrics.widthPixels.toDouble()
    val heightPixels = displayMetrics.heightPixels.toDouble()

    // 获取屏幕的 x 和 y 方向的每英寸像素
    val xDpi = displayMetrics.xdpi.toDouble()
    val yDpi = displayMetrics.ydpi.toDouble()

    // 通过勾股定理计算屏幕对角线的像素数，并转换为英寸
    val widthInches = widthPixels / xDpi
    val heightInches = heightPixels / yDpi
    return sqrt(widthInches * widthInches + heightInches * heightInches)
}

fun getPhysicalScreenSize(context: Context): Double {
    val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    val configuration: Configuration = context.resources.configuration

    // 使用屏幕分辨率计算宽高像素数
    val widthPixels = displayMetrics.widthPixels.toDouble()
    val heightPixels = displayMetrics.heightPixels.toDouble()

    // 获取屏幕的 DPI
    val densityDpi = displayMetrics.densityDpi

    // 获取屏幕的实际密度
    val widthDp = widthPixels / (densityDpi / 160.0)
    val heightDp = heightPixels / (densityDpi / 160.0)

    // 基于屏幕密度计算物理尺寸（英寸）
    val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK

    // 针对不同屏幕尺寸做进一步判断
    return when (screenLayout) {
        Configuration.SCREENLAYOUT_SIZE_XLARGE -> 10.0 // XL 大屏
        Configuration.SCREENLAYOUT_SIZE_LARGE -> 7.0  // 大屏幕
        Configuration.SCREENLAYOUT_SIZE_NORMAL -> 5.0 // 普通屏幕
        Configuration.SCREENLAYOUT_SIZE_SMALL -> 3.5 // 小屏幕
        else -> sqrt((widthDp * widthDp + heightDp * heightDp) / 160)
    }
}

fun dpToPx(context: Context, dp: Float): Int {
    val density = context.resources.displayMetrics.density
    return (dp * density).toInt()
}

fun pxToDp(context: Context, px: Float): Float {
    val density = context.resources.displayMetrics.density
    return px / density
}

@Throws(IOException::class)
fun removePendingFlagFromUri(contentResolver: ContentResolver, uri: Uri) {
    val cv = ContentValues()
    cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
    if (contentResolver.update(uri, cv, null, null) != 1) {
        throw IOException("unable to remove IS_PENDING flag")
    }
}
