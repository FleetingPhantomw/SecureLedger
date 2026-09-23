package com.secureledger.integration.accessibility

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.secureledger.MainActivity
import com.secureledger.core.accessibility.TransactionCandidate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Foreground sheet via CandidateInbox, otherwise a small overlay or notification. */
class PromptDispatcher(private val context: Context) {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val windows = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null

    fun dispatch(candidate: TransactionCandidate, mode: PromptMode) {
        if (AppVisibility.isVisible) {
            CandidateInbox.offer(candidate)
        } else if (mode == PromptMode.OVERLAY) {
            showOverlay(candidate)
        } else {
            showNotification(candidate)
        }
    }

    fun dismissOverlay() {
        overlay?.let { runCatching { windows.removeView(it) } }
        overlay = null
    }

    private fun reviewIntent(candidate: TransactionCandidate): Intent = Intent(context, MainActivity::class.java).apply {
        action = "com.secureledger.REVIEW_${candidate.id}"
        putExtra(MainActivity.EXTRA_CANDIDATE_JSON, Json.encodeToString(candidate))
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    private fun showNotification(candidate: TransactionCandidate) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!notifications.areNotificationsEnabled()) return
        val channelId = "payment_candidates"
        notifications.createNotificationChannel(NotificationChannel(channelId, "消费识别提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val review = PendingIntent.getActivity(
            context, candidate.id.hashCode(), reviewIntent(candidate),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getBroadcast(
            context, candidate.id.hashCode(), Intent(context, DismissCandidateReceiver::class.java).apply {
                putExtra(DismissCandidateReceiver.EXTRA_ID, candidate.id.hashCode())
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle("检测到一笔消费")
            .setContentText("¥${formatAmount(candidate.amountCents)}  ${candidate.merchant.ifBlank { candidate.paymentChannel }}")
            .setContentIntent(review)
            .addAction(android.R.drawable.ic_menu_add, "记一笔", review)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "忽略", dismiss)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        notifications.notify(candidate.id.hashCode(), notification)
    }

    private fun showOverlay(candidate: TransactionCandidate) {
        dismissOverlay()
        val density = context.resources.displayMetrics.density
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 14 * density
                setStroke((1 * density).toInt(), Color.LTGRAY)
            }
            elevation = 8 * density
        }
        card.addView(TextView(context).apply {
            text = "检测到一笔消费 · ¥${formatAmount(candidate.amountCents)}\n${candidate.merchant.ifBlank { candidate.paymentChannel }}"
            setTextColor(Color.rgb(30, 42, 52))
            textSize = 16f
        })
        card.addView(LinearLayout(context).apply {
            gravity = Gravity.END
            addView(Button(context).apply { text = "忽略"; setOnClickListener { dismissOverlay() } })
            addView(Button(context).apply {
                text = "记一笔"
                setOnClickListener { dismissOverlay(); context.startActivity(reviewIntent(candidate)) }
            })
        })
        var downX = 0f
        card.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; false }
                MotionEvent.ACTION_UP -> {
                    if (kotlin.math.abs(event.rawX - downX) > 80 * density) {
                        dismissOverlay()
                        true
                    } else false
                }
                else -> false
            }
        }
        val params = WindowManager.LayoutParams(
            (320 * density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (42 * density).toInt() }
        runCatching { windows.addView(card, params); overlay = card }
        handler.postDelayed({ if (overlay === card) dismissOverlay() }, 8_000)
    }

    private fun formatAmount(cents: Long) = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

class DismissCandidateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(intent.getIntExtra(EXTRA_ID, 0))
    }

    companion object { const val EXTRA_ID = "notification_id" }
}
