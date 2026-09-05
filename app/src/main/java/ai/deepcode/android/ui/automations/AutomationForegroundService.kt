package ai.deepcode.android.ui.automations

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.deepcode.android.util.AppLogger

class AutomationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "automation_foreground_channel"
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_NAME = "Automation Service"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows running automation tasks"
                    setShowBadge(false)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        fun createNotification(context: Context, automationName: String): Notification {
            createNotificationChannel(context)
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Running Automation")
                .setContentText("Executing: $automationName")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()
        }

        fun start(context: Context, automationName: String) {
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                putExtra("automation_name", automationName)
            }
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val areNotificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                notificationManager.areNotificationsEnabled()
            } else {
                true
            }

            try {
                if (hasNotificationPermission && areNotificationsEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                AppLogger.w("AutomationForegroundService", "Cannot start foreground service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutomationForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val automationName = intent?.getStringExtra("automation_name") ?: "Automation"
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val areNotificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }

        if (hasNotificationPermission && areNotificationsEnabled) {
            try {
                val notification = createNotification(this, automationName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                AppLogger.e("AutomationForegroundService", "Failed to start foreground service", e)
            }
        } else {
            AppLogger.w("AutomationForegroundService", "Notification permission not granted. Running in background.")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}