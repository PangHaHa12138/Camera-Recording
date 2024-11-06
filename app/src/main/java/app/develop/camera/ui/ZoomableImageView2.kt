package app.develop.camera.ui

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import androidx.appcompat.widget.AppCompatImageView


import kotlin.math.abs

class ZoomableImageView2 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    lateinit var mMatrix: Matrix

    private var mode = NONE

    private var last = PointF()
    private var start = PointF()
    private var minScale = 1f
    private var maxScale = 3f
    private var m: FloatArray = FloatArray(9)
    private var viewWidth = 0
    private var viewHeight = 0
    private var saveScale = 1f
    private var origWidth = 0f
    private var origHeight = 0f
    private var oldMeasuredWidth = 0
    private var oldMeasuredHeight = 0
    private var mScaleDetector: ScaleGestureDetector? = null

    private var isZoomingDisabled = true

    private var singleClickHandler = Handler(Looper.getMainLooper())


    private var scaleAnimator : ValueAnimator? = null


    init {
        sharedConstructing()
    }

    fun enableZooming() {
        isZoomingDisabled = false
    }

    fun disableZooming() {
        isZoomingDisabled = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun sharedConstructing() {
        super.setClickable(true)
        mScaleDetector = ScaleGestureDetector(context, ScaleListener())
        mMatrix = Matrix()
        imageMatrix = mMatrix
        scaleType = ScaleType.MATRIX

        setOnTouchListener { _, event ->

            true
        }
    }

    private var lastClickTimestampMs : Long = 0L

    private fun onClickEvent(event : MotionEvent) {
        if ((event.eventTime - lastClickTimestampMs) <= DOUBLE_TAP_DELAY) {

            onDoubleClick(event)
        } else {

        }
        lastClickTimestampMs = event.eventTime
    }


    private fun onDoubleClick(event: MotionEvent) {
        // The user hasn't zoomed in
        if (saveScale != 1f) {
            scaleAnimator?.cancel()
            scaleAnimator = ValueAnimator.ofFloat(saveScale, 1f)
            scaleAnimator?.duration = SCALE_ANIMATION_DURATION
            scaleAnimator?.addUpdateListener {
                scaleImageTo(width / 2f, height / 2f, it.animatedValue as Float)
            }

            scaleAnimator?.addListener(object: AnimatorListener {

                var isAnimationCancelled = false

                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                }

                override fun onAnimationCancel(animation: Animator) {
                    isAnimationCancelled = true
                }

                override fun onAnimationRepeat(animation: Animator) {}

            })

            scaleAnimator?.start()
            // Use value animator to animate from saveScale to 1f
        } else {
            // Something similar here
            scaleAnimator?.cancel()
            scaleAnimator = ValueAnimator.ofFloat(1f, DOUBLE_TAP_SCALE)
            scaleAnimator?.duration = SCALE_ANIMATION_DURATION
            scaleAnimator?.addUpdateListener {
                scaleImageTo(event.x, event.y, it.animatedValue as Float)
            }
            scaleAnimator?.start()
        }
    }

    private fun scaleImageTo(pointX: Float, pointY: Float, newScale: Float) {
        val scaleFactor = newScale / saveScale
        scaleImageBy(pointX, pointY, scaleFactor)
    }

    private fun scaleImageBy(pointX: Float, pointY: Float, scaleFactor: Float) {

        val origScale = saveScale
        var sFactor = scaleFactor
        saveScale *= sFactor

        if (saveScale > maxScale) {
            saveScale = maxScale
            sFactor = maxScale / origScale
        } else if (saveScale < minScale) {
            saveScale = minScale
            sFactor = minScale / origScale
        }

        if (origWidth * saveScale <= viewWidth
            || origHeight * saveScale <= viewHeight
        ) mMatrix.postScale(
            sFactor, sFactor, viewWidth / 2f,
            viewHeight / 2f
        ) else mMatrix.postScale(
            sFactor, sFactor,
            pointX, pointY
        )

        fixTrans()

        if (saveScale == 1f) {
            moveOutOfZoomMode()
        } else {
            moveIntoZoomMode()
        }
    }

    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM

            // Cancel ongoing scaling animation (for e.g. on double tap)
            scaleAnimator?.cancel()

            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (isZoomingDisabled) return true
            scaleImageBy(detector.focusX, detector.focusY, detector.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            if (saveScale == 1f) {

            }
        }
    }

    private var isInZoomMode = false

    fun moveIntoZoomMode() {

        if (isInZoomMode) return

        isInZoomMode = true


    }

    fun moveOutOfZoomMode() {

        if (!isInZoomMode) return

        isInZoomMode = false

    }

    fun fixTrans() {

        mMatrix.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]
        val fixTransX = getFixTrans(transX, viewWidth.toFloat(), origWidth * saveScale)
        val fixTransY = getFixTrans(
            transY, viewHeight.toFloat(), origHeight
                    * saveScale
        )
        if (fixTransX != 0f || fixTransY != 0f) mMatrix.postTranslate(fixTransX, fixTransY)
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {

        val minTrans: Float
        val maxTrans: Float
        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        if (trans < minTrans) return -trans + minTrans
        return if (trans > maxTrans) -trans + maxTrans else 0f
    }

    private fun getFixDragTrans(delta: Float, viewSize: Float, contentSize: Float): Float {
        return if (contentSize <= viewSize) {
            0f
        } else delta
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)

        // Rescales image on rotation
        if (oldMeasuredHeight == viewWidth &&
            oldMeasuredHeight == viewHeight || viewWidth == 0 ||
            viewHeight == 0
        ) return

        oldMeasuredHeight = viewHeight
        oldMeasuredWidth = viewWidth

        if (saveScale == 1f) {
            // Fit to screen.
            val scale: Float
            if (drawable?.intrinsicWidth ?: 0 == 0 || drawable?.intrinsicHeight ?: 0 == 0) return
            val bmWidth = drawable.intrinsicWidth
            val bmHeight = drawable.intrinsicHeight
//            Log.d("bmSize", "bmWidth: $bmWidth bmHeight : $bmHeight")
            val scaleX = viewWidth.toFloat() / bmWidth.toFloat()
            val scaleY = viewHeight.toFloat() / bmHeight.toFloat()
            scale = scaleX.coerceAtMost(scaleY)
            mMatrix.setScale(scale, scale)

            // Center the image
            var redundantYSpace = viewHeight.toFloat() - scale * bmHeight.toFloat()
            var redundantXSpace = viewWidth.toFloat() - scale * bmWidth.toFloat()
            redundantYSpace /= 2f
            redundantXSpace /= 2f
            mMatrix.postTranslate(redundantXSpace, redundantYSpace)
            origWidth = viewWidth - 2 * redundantXSpace
            origHeight = viewHeight - 2 * redundantYSpace
            imageMatrix = mMatrix
        }
        fixTrans()
    }

    companion object {
        const val NONE = 0
        const val DRAG = 1
        const val ZOOM = 2
        const val CLICK = 3

        private const val DOUBLE_TAP_DELAY = 200L
        private const val DOUBLE_TAP_SCALE = 1.75F

        private const val SCALE_ANIMATION_DURATION = 300L
    }
}