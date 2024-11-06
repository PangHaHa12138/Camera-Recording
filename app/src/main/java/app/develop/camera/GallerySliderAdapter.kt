package app.develop.camera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import app.develop.camera.capturer.getVideoThumbnail
import app.develop.camera.databinding.GallerySlideBinding
import app.develop.camera.ui.ZoomableImageView
import app.develop.camera.ui.activities.InAppGallery
import app.develop.camera.ui.activities.VideoPlayer
import app.develop.camera.ui.fragment.GallerySlide
import app.develop.camera.util.executeIfAlive
import java.io.InputStream
import kotlin.math.max

class GallerySliderAdapter(
    private val gActivity: InAppGallery,
    val items: ArrayList<CapturedItem>
) : RecyclerView.Adapter<GallerySlide>() {

    var atLeastOneBindViewHolderCall = false

    private val layoutInflater: LayoutInflater = LayoutInflater.from(gActivity)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GallerySlide {
        return GallerySlide(GallerySlideBinding.inflate(layoutInflater, parent, false))
    }

    override fun getItemId(position: Int): Long {
        return items[position].hashCode().toLong()
    }

    override fun onBindViewHolder(holder: GallerySlide, position: Int) {
        val mediaPreview: ZoomableImageView = holder.binding.slidePreview
//        Log.d("GallerySliderAdapter", "postiion $position, preview ${System.identityHashCode(mediaPreview)}")
        val playButton: ImageView = holder.binding.playButton
        val item = items[position]

        mediaPreview.setGalleryActivity(gActivity)
        mediaPreview.disableZooming()
        mediaPreview.setOnClickListener(null)
        mediaPreview.visibility = View.INVISIBLE
        mediaPreview.setImageBitmap(null)

        val placeholderText = holder.binding.placeholderText.root
        if (atLeastOneBindViewHolderCall) {
            placeholderText.visibility = View.VISIBLE
            placeholderText.setText("…")
        }
        atLeastOneBindViewHolderCall = true

        playButton.visibility = View.GONE

        holder.currentPostion = position

        gActivity.asyncImageLoader.executeIfAlive {
            val bitmap: Bitmap? = try {
                if (item.type == ITEM_TYPE_VIDEO) {
                    getVideoThumbnail(gActivity, item.uri)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // Android 9.0 (API 28) 及以上版本，使用 ImageDecoder
                        val source = ImageDecoder.createSource(gActivity.contentResolver, item.uri)
                        ImageDecoder.decodeBitmap(source, ImageDownscaler)
                    } else {
                        // Android 9.0 以下版本，使用 BitmapFactory
                        val inputStream = gActivity.contentResolver.openInputStream(item.uri)
                        decodeBitmapForLowerAPI(inputStream)
                    }
                }
            } catch (e: Exception) { null }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                gActivity.mainExecutor.execute {
                    extracted(
                        holder,
                        position,
                        bitmap,
                        placeholderText,
                        mediaPreview,
                        item,
                        playButton
                    )
                }
            } else {
                gActivity.runOnUiThread {
                    extracted(
                        holder,
                        position,
                        bitmap,
                        placeholderText,
                        mediaPreview,
                        item,
                        playButton
                    )
                }
            }
        }
    }

    private fun extracted(
        holder: GallerySlide,
        position: Int,
        bitmap: Bitmap?,
        placeholderText: TextView,
        mediaPreview: ZoomableImageView,
        item: CapturedItem,
        playButton: ImageView
    ) {
        if (holder.currentPostion == position) {
            if (bitmap != null) {
                placeholderText.visibility = View.GONE
                mediaPreview.visibility = View.VISIBLE
                mediaPreview.setImageBitmap(bitmap)

                if (item.type == ITEM_TYPE_VIDEO) {
                    playButton.visibility = View.VISIBLE
                } else if (item.type == ITEM_TYPE_IMAGE) {
                    mediaPreview.enableZooming()
                }

                mediaPreview.setOnClickListener {
                    val curItem = getCurrentItem()
                    if (curItem.type == ITEM_TYPE_VIDEO) {
                        val intent = Intent(gActivity, VideoPlayer::class.java)
                        intent.putExtra(VideoPlayer.VIDEO_URI, curItem.uri)
                        intent.putExtra(VideoPlayer.IN_SECURE_MODE, gActivity.isSecureMode)

                        gActivity.startActivity(intent)
                    }
                }
            } else {
                mediaPreview.visibility = View.INVISIBLE

                val resId = if (item.type == ITEM_TYPE_IMAGE) {
                    R.string.inaccessible_image
                } else {
                    R.string.inaccessible_video
                }

                placeholderText.visibility = View.VISIBLE
                placeholderText.setText(gActivity.getString(resId, item.dateString))
            }
        } else {
            bitmap?.recycle()
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

    fun removeItem(item: CapturedItem) {
        removeChildAt(items.indexOf(item))
    }

    private fun removeChildAt(index: Int) {
        items.removeAt(index)

        // Close gallery if no files are present
        if (items.isEmpty()) {
            gActivity.showMessage(
                gActivity.getString(R.string.existing_no_image)
            )
            gActivity.finish()
        }

        notifyItemRemoved(index)
    }

    fun getCurrentItem(): CapturedItem {
        return items[gActivity.gallerySlider.currentItem]
    }

    override fun getItemCount(): Int {
        return items.size
    }
}

@RequiresApi(Build.VERSION_CODES.P)
object ImageDownscaler : ImageDecoder.OnHeaderDecodedListener {
    override fun onHeaderDecoded(decoder: ImageDecoder,
        info: ImageDecoder.ImageInfo, source: ImageDecoder.Source) {
        val size = info.size
        val w = size.width
        val h = size.height
        // limit the max size of the bitmap to avoid bumping into bitmap size limit
        // (100 MB)
        val largerSide = max(w, h)
        val maxSide = 4500

        if (largerSide > maxSide) {
            val ratio = maxSide.toDouble() / largerSide
            decoder.setTargetSize((ratio * w).toInt(), (ratio * h).toInt())
        }
    }
}
