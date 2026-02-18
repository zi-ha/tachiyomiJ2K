package eu.kanade.tachiyomi.data.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupRestoreJob
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.notificationManager
import uy.kohesive.injekt.api.get
import java.io.File
import eu.kanade.tachiyomi.BuildConfig.APPLICATION_ID as ID

/**
 * Global [BroadcastReceiver] that runs on UI thread
 * Pending Broadcasts should be made from here.
 * NOTE: Use local broadcasts if possible.
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            // Dismiss notification
            ACTION_DISMISS_NOTIFICATION -> dismissNotification(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
            // Delete image from path and dismiss notification
            ACTION_DELETE_IMAGE ->
                deleteImage(
                    context,
                    intent.getStringExtra(EXTRA_FILE_LOCATION)!!,
                    intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1),
                )
            ACTION_CANCEL_RESTORE -> cancelRestoreUpdate(context)
            // Share backup file
            ACTION_SHARE_BACKUP ->
                shareBackup(
                    context,
                    intent.getParcelableExtra(EXTRA_URI)!!,
                    intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1),
                )
            // Share crash dump file
            ACTION_SHARE_CRASH_LOG ->
                shareFile(
                    context,
                    intent.getParcelableExtra(EXTRA_URI)!!,
                    "text/plain",
                    intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1),
                )
        }
    }

    /**
     * Dismiss the notification
     *
     * @param notificationId the id of the notification
     */
    private fun dismissNotification(
        context: Context,
        notificationId: Int,
    ) {
        context.notificationManager.cancel(notificationId)
    }

    /**
     * Called to start share intent to share backup file
     *
     * @param context context of application
     * @param path path of file
     * @param notificationId id of notification
     */
    private fun shareBackup(
        context: Context,
        uri: Uri,
        notificationId: Int,
    ) {
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/x-protobuf+gzip"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        // Dismiss notification
        dismissNotification(context, notificationId)
        // Launch share activity
        context.startActivity(sendIntent)
    }

    /**
     * Called to start share intent to share backup file
     *
     * @param context context of application
     * @param path path of file
     * @param notificationId id of notification
     */
    private fun shareFile(
        context: Context,
        uri: Uri,
        fileMimeType: String,
        notificationId: Int,
    ) {
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                type = fileMimeType
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        // Dismiss notification
        dismissNotification(context, notificationId)
        // Launch share activity
        context.startActivity(sendIntent)
    }

    /**
     * Called to delete image
     *
     * @param path path of file
     * @param notificationId id of notification
     */
    private fun deleteImage(
        context: Context,
        path: String,
        notificationId: Int,
    ) {
        // Dismiss notification
        dismissNotification(context, notificationId)

        // Delete file
        val file = File(path)
        file.delete()

        DiskUtil.scanMedia(context, file)
    }

    /** Method called when user wants to stop a restore
     *
     * @param context context of application
     */
    private fun cancelRestoreUpdate(context: Context) {
        BackupRestoreJob.stop(context)
    }

    companion object {
        private const val NAME = "NotificationReceiver"

        // Called to delete image.
        private const val ACTION_DELETE_IMAGE = "$ID.$NAME.DELETE_IMAGE"

        // Called to launch send intent.
        private const val ACTION_SHARE_BACKUP = "$ID.$NAME.SEND_BACKUP"

        private const val ACTION_SHARE_CRASH_LOG = "$ID.$NAME.SEND_CRASH_LOG"

        // Called to cancel restore
        private const val ACTION_CANCEL_RESTORE = "$ID.$NAME.CANCEL_RESTORE"

        // Value containing file location.
        private const val EXTRA_FILE_LOCATION = "$ID.$NAME.FILE_LOCATION"

        // Called to dismiss notification.
        private const val ACTION_DISMISS_NOTIFICATION = "$ID.$NAME.ACTION_DISMISS_NOTIFICATION"

        // Value containing uri.
        private const val EXTRA_URI = "$ID.$NAME.URI"

        // Value containing notification id.
        private const val EXTRA_NOTIFICATION_ID = "$ID.$NAME.NOTIFICATION_ID"

        // Value containing group id.
        private const val EXTRA_GROUP_ID = "$ID.$NAME.EXTRA_GROUP_ID"

        // Value containing manga id.
        private const val EXTRA_MANGA_ID = "$ID.$NAME.EXTRA_MANGA_ID"

        /**
         * Returns [PendingIntent] that starts a service which dismissed the notification
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun dismissNotificationPendingBroadcast(
            context: Context,
            notificationId: Int,
        ): PendingIntent {
            val intent =
                Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_DISMISS_NOTIFICATION
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /**
         * Returns [PendingIntent] that starts a service which dismissed the notification
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun dismissNotification(
            context: Context,
            notificationId: Int,
            groupId: Int? =
                null,
        ) {
            val groupKey =
                context.notificationManager.activeNotifications
                    .find {
                        it.id == notificationId
                    }?.groupKey
            if (groupId != null && groupId != 0 && groupKey != null && groupKey.isNotEmpty()) {
                val notifications =
                    context.notificationManager.activeNotifications.filter {
                        it.groupKey == groupKey
                    }
                if (notifications.size == 2) {
                    context.notificationManager.cancel(groupId)
                    return
                }
            }
            context.notificationManager.cancel(notificationId)
        }

        /**
         * Returns [PendingIntent] that starts a service which cancels the notification and starts a share activity
         *
         * @param context context of application
         * @param path location path of file
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun shareImagePendingBroadcast(
            context: Context,
            path: String,
            notificationId: Int,
        ): PendingIntent {
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    val uri = File(path).getUriCompat(context)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    clipData = ClipData.newRawUri(null, uri)
                    type = "image/*"
                }
            return PendingIntent.getActivity(
                context,
                0,
                shareIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that starts a service which removes an image from disk
         *
         * @param context context of application
         * @param path location path of file
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun deleteImagePendingBroadcast(
            context: Context,
            path: String,
            notificationId: Int,
        ): PendingIntent {
            val intent =
                Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_DELETE_IMAGE
                    putExtra(EXTRA_FILE_LOCATION, path)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /**
         * Returns [PendingIntent] that starts a reader activity containing chapter.
         *
         * @param context context of application
         * @param manga manga of chapter
         * @param chapter chapter that needs to be opened
         */
        internal fun openChapterPendingActivity(
            context: Context,
            manga: Manga,
            chapter: Chapter,
        ): PendingIntent {
            val newIntent = ReaderActivity.newIntent(context, manga, chapter)
            return PendingIntent.getActivity(
                context,
                manga.id.hashCode(),
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that opens the manga details controller.
         *
         * @param context context of application
         * @param manga manga of chapter
         */
        internal fun openChapterPendingActivity(
            context: Context,
            manga: Manga,
            groupId: Int,
        ): PendingIntent {
            val newIntent =
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.SHORTCUT_MANGA)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MangaDetailsController.MANGA_EXTRA, manga.id)
                    .putExtra("notificationId", manga.id.hashCode())
                    .putExtra("groupId", groupId)
            return PendingIntent.getActivity(
                context,
                manga.id.hashCode(),
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Returns [PendingIntent] that opens the error or skipped log file in an external viewer
         *
         * @param context context of application
         * @param uri uri of error or skipped log file
         * @return [PendingIntent]
         */
        internal fun openErrorOrSkippedLogPendingActivity(
            context: Context,
            uri: Uri,
        ): PendingIntent {
            val intent =
                Intent().apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(uri, "text/plain")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        /**
         * Returns [PendingIntent] that starts a share activity for a backup file.
         *
         * @param context context of application
         * @param uri uri of backup file
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun shareBackupPendingBroadcast(
            context: Context,
            uri: Uri,
            notificationId: Int,
        ): PendingIntent {
            val intent =
                Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_SHARE_BACKUP
                    putExtra(EXTRA_URI, uri)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /**
         * Returns [PendingIntent] that starts a share activity for a crash log dump file.
         *
         * @param context context of application
         * @param uri uri of file
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun shareCrashLogPendingBroadcast(
            context: Context,
            uri: Uri,
            notificationId: Int,
        ): PendingIntent {
            val intent =
                Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_SHARE_CRASH_LOG
                    putExtra(EXTRA_URI, uri)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /**
         * Returns [PendingIntent] that cancels a backup restore job.
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun cancelRestorePendingBroadcast(
            context: Context,
            notificationId: Int,
        ): PendingIntent {
            val intent =
                Intent(context, NotificationReceiver::class.java).apply {
                    action = ACTION_CANCEL_RESTORE
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
