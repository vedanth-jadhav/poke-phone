package co.interaction.pokephone.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.interaction.pokephone.R
import co.interaction.pokephone.capture.CaptureActivity

object NotificationHelper {
    private const val READY_CHANNEL = "poke_capture_ready"
    private const val READY_NOTIFICATION_ID = 3101

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            READY_CHANNEL,
            context.getString(R.string.ready_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.ready_notification_body)
            setShowBadge(false)
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun showReadyNotification(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        ensureChannels(context)

        val captureIntent = Intent(context, CaptureActivity::class.java)
            .putExtra(CaptureActivity.EXTRA_SOURCE, "notification")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            captureIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, READY_CHANNEL)
            .setSmallIcon(R.drawable.ic_poke_mark)
            .setContentTitle(context.getString(R.string.ready_notification_title))
            .setContentText(context.getString(R.string.ready_notification_body))
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(context).notify(READY_NOTIFICATION_ID, notification)
        return true
    }
}
