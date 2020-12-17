package com.safetica.datasafe.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.PluralsRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.safetica.datasafe.R
import com.safetica.datasafe.activity.EncryptionActivity
import com.safetica.datasafe.interfaces.ICryptoManager
import com.safetica.datasafe.interfaces.IDatabaseManager
import com.safetica.datasafe.interfaces.Progress
import com.safetica.datasafe.model.Image
import dagger.android.AndroidInjection
import kotlinx.coroutines.*
import javax.crypto.SecretKey
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext


/**
 * Handles cryptographic operations on background
 */
class CryptoService : Service(), CoroutineScope {

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job


    @Inject
    internal lateinit var databaseManager: IDatabaseManager

    @Inject
    internal lateinit var cryptoManager: ICryptoManager

    override fun onBind(p0: Intent?): IBinder? {
        return EncryptionServiceBinder()
    }

    companion object {
        const val BROADCAST = "com.safetica.datasafe.encryptionbroadcast"
        const val HOW_MANY = "com.safetica.datasafe.howmany"
        const val FROM = "com.safetica.datasafe.from"
        const val CHANNEL_ID = "com.safetica.datasafe.encryptionservice"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        coroutineContext.cancelChildren()
        super.onDestroy()
    }


    /**
     * Encrypts images with given [uris]
     * @param token to use as a key in encryption
     * @param uris of images to be encrypted
     */
    fun encryptImages(token: SecretKey, vararg uris: Uri) {

        if (uris.isEmpty()) return

        val notificationBuilder = getNotificationBuilder(uris.size)

        startForeground(notificationBuilder, uris.size)

        launch {
            var counter = 0
            val images = cryptoManager.encrypt(object: Progress {
                override fun update() {
                    counter++
                    updateNotification(notificationBuilder, uris.size, counter)
                }
            }, token, *uris)
            databaseManager.insertImages(*images)

            sendProgressBroadcast(counter, 0)

            endForeground(counter, R.plurals.n_images_encrypted, notificationBuilder)
        }


    }

    private fun notificationChannel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(CHANNEL_ID, CryptoService::class.java.name)
        } else {
            ""
        }
    }

    private fun pendingIntent(): PendingIntent? {
        val notificationIntent = Intent(this, EncryptionActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 1,
            notificationIntent, 0
        )
        return pendingIntent
    }

    /**
     * Decrypts given [images]
     * @param token to use as a key in decryption
     * @param images to be decrypted
     */
    fun decryptImages(token: SecretKey, vararg images: Image) {

        if (images.isEmpty()) return

        val notificationBuilder = getNotificationBuilder(images.size)

        startForeground(notificationBuilder, images.size)

        launch {
            var counter = 0
            val decrypted = cryptoManager.decrypt(object: Progress {
                override fun update() {
                    counter++
                    updateNotification(notificationBuilder, images.size, counter)
                }
            }, token, *images)

            databaseManager.insertImages(*decrypted)

            endForeground(counter, R.plurals.n_images_decrypted, notificationBuilder)
        }

    }

    /**
     * Creates notification builder with progressbar
     * @param size of the progressbar
     * @return notification builder
     */
    private fun getNotificationBuilder(size: Int): NotificationCompat.Builder {
        val channelId = notificationChannel()
        val pendingIntent = pendingIntent()
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(resources.getString(R.string.decrypting))
            .setProgress(size, 0, false)
            .setContentIntent(pendingIntent)
    }

    /**
     * Rewrites keys of given [images]
     * @param oldToken to use as a key to decrypt stored key
     * @param newToken to use as a key to encrypt decrypted key
     * @param images
     */
    fun rewriteKeys(oldToken: SecretKey, newToken: SecretKey, vararg images: Image) {

        if (images.isEmpty()) return

        val notificationBuilder = getNotificationBuilder(images.size)

        startForeground(notificationBuilder, images.size)

        launch {
            var counter = 0
            val decrypted = cryptoManager.import(object: Progress {
                override fun update() {
                    counter++
                    updateNotification(notificationBuilder, images.size, counter)
                }
            }, oldToken, newToken, *images)

            databaseManager.insertImages(*decrypted)

            endForeground(counter, R.plurals.n_keys_rewritten, notificationBuilder)
        }
    }


    /**
     * Imports images with given [uris]
     * @param oldToken to use as a key to decrypt stored key
     * @param newToken to use as a key to encrypt decrypted key
     * @param uris of images to be imported
     */
    fun importImages(oldToken: SecretKey, newToken: SecretKey, vararg uris: Uri) {

        if (uris.isEmpty()) return

        val notificationBuilder = getNotificationBuilder(uris.size)

        startForeground(notificationBuilder, uris.size)

        launch {
            var counter = 0
            val decrypted = cryptoManager.rewriteKey(object: Progress {
                override fun update() {
                    counter++
                    updateNotification(notificationBuilder, uris.size, counter)
                }
            }, oldToken, newToken, *uris)

            databaseManager.insertImages(*decrypted)

            endForeground(counter, R.plurals.n_images_imported, notificationBuilder)
        }
    }

    /**
     * Stops service, enables to hide notification
     * @param counter
     * @param messageId to be displayed at the end
     * @param notificationBuilder to build  notification
     */
    private fun endForeground(counter: Int, @PluralsRes messageId: Int, notificationBuilder: NotificationCompat.Builder) {
        sendProgressBroadcast(counter, 0)
        startForeground(
            1, notificationBuilder
                .setContentTitle(resources.getString(R.string.app_name))
                .setProgress(0, 0, false)
                .setContentText(resources.getQuantityString(messageId, counter, counter)).build()
        )
        stopForeground(false)
    }

    /**
     * Updates progressbar on notification
     * @param notificationBuilder to build notification
     * @param size of the progressbar
     * @param counter
     */
    private fun updateNotification(notificationBuilder: NotificationCompat.Builder, size: Int, counter: Int) {
        startForeground(1, notificationBuilder.setProgress(size, counter, false)
                .setContentText("$counter/$size").build())
        sendProgressBroadcast(counter, size)
    }

    /**
     * Shows notification with progressbar
     * @param size of the progressbar
     * @param notificationBuilder to build notification
     */
    private fun startForeground(notificationBuilder: NotificationCompat.Builder, size: Int) {
        startForeground(1, notificationBuilder.build())
        sendProgressBroadcast(0, size)
    }


    /**
     * Sends broadcast with information about progress
     * @param howMany pictures has beenencrypted/decrypted/imported ...
     * @param from how many pictures in total
     */
    private fun sendProgressBroadcast(howMany: Int, from: Int) {
        val intent = Intent(BROADCAST)
        intent.putExtra(HOW_MANY, howMany)
        intent.putExtra(FROM, from)
        sendBroadcast(intent)
    }

    /**
     * Binds service to an activity
     */
    inner class EncryptionServiceBinder : Binder() {
        internal val service: CryptoService
            get() = this@CryptoService
    }

    /**
     * Creates notification channel
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }
}

