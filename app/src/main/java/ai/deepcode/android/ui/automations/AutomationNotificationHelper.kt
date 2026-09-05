package ai.deepcode.android.ui.automations

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.deepcode.android.R
import ai.deepcode.android.ui.MainActivity
import ai.deepcode.android.ui.components.cleanControlAndCitationTokens
import ai.deepcode.android.util.AppLogger
import kotlin.math.abs

object AutomationNotificationHelper {
    private const val TAG = "AutomationNotification"
    const val CHANNEL_ID = "automation_completed_channel"
    private const val CHANNEL_NAME = "Automations & Scheduled Tasks"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts and results when scheduled automations execute"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showCompletionNotification(
        context: Context,
        automationId: String,
        ruleName: String,
        sessionId: String,
        output: String,
        isChatGPT: Boolean
    ) {
        try {
            createNotificationChannel(context)

            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!hasPermission || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !manager.areNotificationsEnabled())) {
                AppLogger.w(TAG, "Notification permission not granted or notifications disabled")
                return
            }

            val cleanOutput = cleanControlAndCitationTokens(output)
                .replace(Regex("""(?m)^#+\s*"""), "")
                .replace("**", "")
                .replace("*", "")
                .replace("`", "")
                .trim()

            val preview = cleanOutput.lines()
                .filter { it.isNotBlank() }
                .take(5)
                .joinToString("\n")
                .ifEmpty { "Automation executed successfully." }

            val shortText = cleanOutput.lines().firstOrNull { it.isNotBlank() } ?: "Task completed"

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = "ai.deepcode.android.action.OPEN_SESSION"
                putExtra("target_session_id", sessionId)
                putExtra("target_tab", 1)
                putExtra("is_chatgpt", isChatGPT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val notificationId = abs(automationId.hashCode()).let { if (it == 0) 1002 else it }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (isChatGPT) "🤖 $ruleName (ChatGPT)" else "🤖 $ruleName"

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(shortText)
                .setSubText("Automation Complete")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(preview)
                        .setSummaryText("Scheduled Task")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_view,
                    "View in Chat",
                    pendingIntent
                )
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            manager.notify(notificationId, builder.build())
            AppLogger.i(TAG, "Posted completion notification for rule '$ruleName' (id=$notificationId)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to post completion notification for $ruleName", e)
        }
    }

    fun showFailureNotification(
        context: Context,
        automationId: String,
        ruleName: String,
        sessionId: String,
        errorMessage: String
    ) {
        try {
            createNotificationChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = abs((automationId + "_fail").hashCode()).let { if (it == 0) 1003 else it }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = "ai.deepcode.android.action.OPEN_SESSION"
                putExtra("target_session_id", sessionId)
                putExtra("target_tab", 1)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⚠️ Automation Failed: $ruleName")
                .setContentText(errorMessage)
                .setStyle(NotificationCompat.BigTextStyle().bigText("Task failed to execute: $errorMessage"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_ERROR)

            manager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to post failure notification for $ruleName", e)
        }
    }
}
