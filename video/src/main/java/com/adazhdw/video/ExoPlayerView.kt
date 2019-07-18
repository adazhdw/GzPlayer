package com.adazhdw.video

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.annotation.RequiresPermission
import androidx.core.widget.ContentLoadingProgressBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoListener
import java.util.*

class ExoPlayerView : FrameLayout {

    private val TIME_UNSET = java.lang.Long.MIN_VALUE + 1
    private val TAG = "ExoPlayerView"
    private val mExoPlayer: SimpleExoPlayer
    private val mExoPost: ImageView
    private val loadingBar: ContentLoadingProgressBar
    private val startIv: ImageView
    private val mExoTextureView: TextureView
    private val bottomLayout: View
    private val mVideoViewRoot: AspectRatioFrameLayout
    private val controlViewFl: View
    private val currentTime: TextView
    private val durationTime: TextView
    private val mSeekBar: SeekBar
    private val mFormatBuilder: StringBuilder
    private val mFormatter: Formatter
    private val mExoControlDispatcher: ExoControlDispatcher
    private val mHandler: Handler = Handler(Looper.getMainLooper())
    private var isSetUped = false

    constructor(@NonNull context: Context) : this(context, null)
    constructor(@NonNull context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(@NonNull context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        addView(LayoutInflater.from(context).inflate(R.layout.exo_video_viewer, this, false))
        mExoPlayer = ExoPlayerFactory.newSimpleInstance(context)
        mVideoViewRoot = findViewById(R.id.mVideoViewRoot)
        mExoPost = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
        }
        mExoTextureView = findViewById(R.id.mExoTextureView)
        setBackgroundColor(Color.parseColor("#000000"))
        addView(mExoPost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        //Control View
        addView(LayoutInflater.from(context).inflate(R.layout.exo_player_viewer_control, this, false))
        controlViewFl = findViewById(R.id.controlViewFl)
        loadingBar = findViewById(R.id.loadingBar)
        startIv = findViewById(R.id.startIv)
        bottomLayout = findViewById(R.id.bottomLayout)
        currentTime = findViewById(R.id.currentTime)
        durationTime = findViewById(R.id.durationTime)
        mSeekBar = findViewById(R.id.mSeekBar)
        mFormatBuilder = StringBuilder()
        mFormatter = Formatter(mFormatBuilder, Locale.getDefault())
        mExoControlDispatcher = ExoControlDispatcher()
        initView()
    }


    private fun initView() {

        mExoPlayer.addVideoListener(object : VideoListener {
            override fun onRenderedFirstFrame() {
                mSeekBar.max = (mExoPlayer.duration / 1000).toInt()
                showControlView()
                loadingBar.hide()
                setVideoProgress()
            }

            override fun onVideoSizeChanged(
                width: Int,
                height: Int,
                unappliedRotationDegrees: Int,
                pixelWidthHeightRatio: Float
            ) {
                aspectRatio(width, height, pixelWidthHeightRatio, unappliedRotationDegrees)
            }

        })
        mExoPlayer.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        seekToStart()
                    }
                }
            }

            override fun onPlayerError(error: ExoPlaybackException?) {
                showControlView()
                loadingBar.hide()
                if (error != null) {
                    handleError(error)
                }
            }
        })
        mSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                seekTo(progress * 1000L)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                pausePlay()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                startPlay()
            }
        })
        startIv.setOnClickListener {
            if (isPlaying()) {
                pausePlay()
            } else {
                startPlay()
                startHideControl()
            }
        }
        mVideoViewRoot.setOnClickListener {
            if (isSetUped) {
                if (startIv.visibility == View.VISIBLE || bottomLayout.visibility == View.VISIBLE) {
                    showControlView(false)
                } else {
                    showControlView()
                    startHideControl()
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.INTERNET)
    fun setDataSource(path: String?, isAutoPlay: Boolean = false, lifecycle: Lifecycle? = null, errorListener: ((errorType: Int) -> Unit)?=null) {
        if (path.isNullOrBlank()) return
        loadingBar.visibility = View.VISIBLE
        loadingBar.show()
        mExoPlayer.setUp(context, path, isAutoPlay)
        isSetUped = true
        this.errorListener = errorListener
        lifecycle?.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fun onPause() {
                this@ExoPlayerView.pausePlay()
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun onDestroy() {
                this@ExoPlayerView.release()
            }
        })
    }

    private fun startPlay() {
        if (mExoPlayer.playbackState == Player.STATE_ENDED) {
            mExoControlDispatcher.dispatchSeekTo(mExoPlayer, mExoPlayer.currentWindowIndex, TIME_UNSET)
        }
        mExoPost.visibility = View.GONE
        startIv.setImageResource(R.drawable.ic_player_pause)
        mExoControlDispatcher.dispatchSetPlayWhenReady(mExoPlayer, true)
        setVideoProgress()
        startProgressRunnable()
    }

    private fun pausePlay() {
        mExoControlDispatcher.dispatchSetPlayWhenReady(mExoPlayer, false)
        startIv.setImageResource(R.drawable.ic_player_start)
        mHandler.removeCallbacks(mDismissRunnable)
        mHandler.removeCallbacks(mProgressRunnable)
    }

    fun release() {
        mExoPlayer.release()
        mHandler.removeCallbacks(mDismissRunnable)
        mHandler.removeCallbacks(mProgressRunnable)
    }

    fun getPoster():ImageView{
        return mExoPost
    }
    private fun seekTo(progress: Long) {
        mExoControlDispatcher.dispatchSeekTo(mExoPlayer, mExoPlayer.currentWindowIndex, progress)
        setVideoProgress()
    }

    /**
     * 强制Seek到播放开始状态，以便重新开始播放
     */
    private fun seekToStart() {
        mHandler.postDelayed({
            pausePlay()
            mExoControlDispatcher.dispatchSeekTo(
                mExoPlayer,
                mExoPlayer.currentWindowIndex,
                TIME_UNSET
            )
        }, 1000)
    }

    /**
     * 判断是否正在播放
     */
    private fun isPlaying(): Boolean {
        return mExoPlayer.playbackState != Player.STATE_ENDED
                && mExoPlayer.playbackState != Player.STATE_IDLE
                && mExoPlayer.playWhenReady
    }

    /**\
     * 更新视频进度
     */
    private fun setVideoProgress() {
        val duration = mExoPlayer.duration
        val currentPosition = mExoPlayer.currentPosition
        currentTime.text = stringForTime(currentPosition)
        durationTime.text = stringForTime(duration)
        mSeekBar.progress = (currentPosition / 1000).toInt()
    }

    /**
     * 格式化时间进度
     */
    private fun stringForTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000

        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600

        mFormatBuilder.setLength(0)
        return if (hours > 0) {
            mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        } else {
            mFormatter.format("%02d:%02d", minutes, seconds).toString()
        }
    }

    /**
     * 进度条更新Runnable
     */
    private val mProgressRunnable: Runnable = Runnable {
        setVideoProgress()
        startProgressRunnable()
    }

    private fun startProgressRunnable() {
        mHandler.removeCallbacks(mProgressRunnable)
        mHandler.postDelayed(mProgressRunnable, 1000)
    }

    /**
     * ControlView 消失Runnable
     */
    private fun startHideControl() {
        mHandler.removeCallbacks(mDismissRunnable)
        mHandler.postDelayed(mDismissRunnable, 3000)
    }

    private val mDismissRunnable: Runnable = Runnable {
        showControlView(false)
    }

    private fun showControlView(isShow: Boolean = true) {
        startIv.visibility = if (isShow) View.VISIBLE else View.INVISIBLE
        bottomLayout.visibility = if (isShow) View.VISIBLE else View.INVISIBLE
    }

    /**
     * 调整TextureView宽高以适应视频大小和屏幕大小
     */
    private fun aspectRatio(
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float,
        unappliedRotationDegrees: Int
    ) {
        val aspectRatio =
            if (videoHeight == 0 || videoWidth == 0) 1f else videoWidth.toFloat() * pixelWidthHeightRatio / videoHeight
        applyTextureViewRotation(mExoTextureView, unappliedRotationDegrees)
        //设置TextureView Layout 宽高
        mVideoViewRoot.setAspectRatio(aspectRatio)
    }

    /**
     * 设置TextureView 宽高
     */
    private fun applyTextureViewRotation(mExoTextureView: TextureView, textureViewRotation: Int) {
        val textureViewWidth = mExoTextureView.width.toFloat()
        val textureViewHeight = mExoTextureView.height.toFloat()
        if (textureViewWidth == 0f || textureViewHeight == 0f || textureViewRotation == 0) {
            mExoTextureView.setTransform(null)
        } else {
            val transformMatrix = Matrix()
            val pivotX = textureViewWidth / 2
            val pivotY = textureViewHeight / 2
            transformMatrix.postRotate(textureViewRotation.toFloat(), pivotX, pivotY)

            // After rotation, scale the rotated texture to fit the TextureView size.
            val originalTextureRect = RectF(0f, 0f, textureViewWidth, textureViewHeight)
            val rotatedTextureRect = RectF()
            transformMatrix.mapRect(rotatedTextureRect, originalTextureRect)
            transformMatrix.postScale(
                textureViewWidth / rotatedTextureRect.width(),
                textureViewHeight / rotatedTextureRect.height(),
                pivotX,
                pivotY
            )
            mExoTextureView.setTransform(transformMatrix)
        }
    }

    private fun SimpleExoPlayer.setUp(context: Context, url: String?, autoPlay: Boolean = false) {
        videoComponent?.clearVideoTextureView(mExoTextureView)
        videoComponent?.setVideoTextureView(mExoTextureView)
        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))
        val mediaSource = buildMediaSource(Uri.parse(url), dataSourceFactory)
        prepare(mediaSource)
        playWhenReady = autoPlay
        if (autoPlay) {
            startPlay()
        } else {
            pausePlay()
        }
    }

    private fun buildMediaSource(uri: Uri, dataSourceFactory: DataSource.Factory): MediaSource {
        return when (val type = Util.inferContentType(uri)) {
//            C.TYPE_DASH -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
//            C.TYPE_SS -> SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
//            C.TYPE_HLS -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            C.TYPE_OTHER -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            else -> throw IllegalStateException("Unsupported type: $type")
        }
    }

    /**
     * 处理播放器错误
     */
    private var isVideoError = false
    private var errorListener: ((errorType: Int) -> Unit)? = null
    private fun handleError(error: ExoPlaybackException) {
        isVideoError = true
        errorListener?.invoke(error.type)
        when (error.type) {
            ExoPlaybackException.TYPE_SOURCE -> {
                Log.d(TAG, "ExoPlaybackException-----TYPE_SOURCE")
            }
            ExoPlaybackException.TYPE_RENDERER -> {
                Log.d(TAG, "ExoPlaybackException-----TYPE_RENDERER")
            }
            ExoPlaybackException.TYPE_UNEXPECTED -> {
                Log.d(TAG, "ExoPlaybackException-----TYPE_UNEXPECTED")
            }
            ExoPlaybackException.TYPE_REMOTE -> {
                Log.d(TAG, "ExoPlaybackException-----TYPE_REMOTE")
            }
            ExoPlaybackException.TYPE_OUT_OF_MEMORY -> {
                Log.d(TAG, "ExoPlaybackException-----TYPE_OUT_OF_MEMORY")
            }
        }
    }

}