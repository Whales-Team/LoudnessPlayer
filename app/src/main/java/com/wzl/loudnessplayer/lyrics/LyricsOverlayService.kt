package com.wzl.loudnessplayer.lyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.wzl.loudnessplayer.MainActivity
import com.wzl.loudnessplayer.R
import com.wzl.loudnessplayer.data.TrackRepository
import kotlin.math.roundToInt

class LyricsOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var primaryText: TextView? = null
    private var secondaryText: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        createOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                TrackRepository(this).setLyricsOverlayEnabled(false)
                stopSelf()
            }

            ACTION_UPDATE -> updateText(
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty(),
                lyricLine = intent.getStringExtra(EXTRA_LYRIC_LINE),
            )
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        primaryText = null
        secondaryText = null
        isRunning = false
        super.onDestroy()
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(Color.argb(218, 24, 28, 25))
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        primaryText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            maxLines = 2
            text = "歌词悬浮窗已开启"
        }
        secondaryText = TextView(this).apply {
            setTextColor(Color.argb(190, 255, 255, 255))
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
            text = "打开音乐开始播放"
        }
        textColumn.addView(
            primaryText,
            LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        textColumn.addView(
            secondaryText,
            LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        val closeButton = TextView(this).apply {
            text = "×"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "关闭歌词悬浮窗"
            setOnClickListener {
                TrackRepository(this@LyricsOverlayService).setLyricsOverlayEnabled(false)
                stopSelf()
            }
        }
        container.addView(textColumn)
        container.addView(closeButton, LinearLayout.LayoutParams(dp(36), dp(44)))

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(96)
        }
        attachDragHandler(container)
        runCatching {
            windowManager.addView(container, layoutParams)
            overlayView = container
        }.onFailure {
            TrackRepository(this).setLyricsOverlayEnabled(false)
            stopSelf()
        }
    }

    private fun attachDragHandler(view: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).roundToInt()
                    params.y = startY + (event.rawY - touchY).roundToInt()
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                    true
                }

                else -> false
            }
        }
    }

    private fun updateText(
        title: String,
        artist: String,
        lyricLine: String?,
    ) {
        primaryText?.text = lyricLine?.takeIf(String::isNotBlank) ?: title.ifBlank { "暂无歌词" }
        secondaryText?.text = listOf(title, artist)
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .ifBlank { "响度播放器" }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "桌面歌词",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "保持桌面歌词悬浮窗运行"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, LyricsOverlayService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("桌面歌词正在显示")
            .setContentText("点击返回响度播放器")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "关闭",
                    stopPendingIntent,
                ).build(),
            )
            .build()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val ACTION_START = "com.wzl.loudnessplayer.lyrics.START"
        private const val ACTION_UPDATE = "com.wzl.loudnessplayer.lyrics.UPDATE"
        private const val ACTION_STOP = "com.wzl.loudnessplayer.lyrics.STOP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_LYRIC_LINE = "lyric_line"
        private const val NOTIFICATION_CHANNEL_ID = "lyrics_overlay"
        private const val NOTIFICATION_ID = 1102

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, LyricsOverlayService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun update(
            context: Context,
            title: String,
            artist: String,
            lyricLine: String?,
        ) {
            if (!isRunning) return
            val intent = Intent(context, LyricsOverlayService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_LYRIC_LINE, lyricLine)
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LyricsOverlayService::class.java))
        }
    }
}
