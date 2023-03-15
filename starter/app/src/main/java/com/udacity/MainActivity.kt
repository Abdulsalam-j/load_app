package com.udacity

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.udacity.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var downloadID: Long = 0
    private var downloadStatus = "Fail"
    private var selectedDownloadUri: URL? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var notificationManager: NotificationManager
    private lateinit var pendingIntent: PendingIntent
    private lateinit var action: NotificationCompat.Action

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pushNotificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pushNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        createNotificationChannel()

        binding.downloadOptionsRadioGroup.setOnCheckedChangeListener { _, i ->
            selectedDownloadUri = when (i) {
                R.id.retrofit_radio_button -> URL.RETROFIT_URI
                R.id.udacity_radio_button -> URL.UDACITY_URI
                R.id.glide_radio_button -> URL.GLIDE_URI
                else -> URL.RETROFIT_URI
            }
        }

        binding.customButton.setOnClickListener {
            if (selectedDownloadUri != null) {
                val isConnected = isOnline(this)
                if (isConnected) {
                    binding.customButton.buttonState = ButtonState.Loading
                    download()
                } else {
                    Toast.makeText(this, getString(R.string.no_network_toast), Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
                Toast.makeText(this, getString(R.string.select_option_toast), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadID == id) {
                downloadStatus = "Success"
                binding.customButton.buttonState = ButtonState.Completed
                createNotification()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "LoadAppChannel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(false)
            }
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.BLUE
            notificationChannel.enableVibration(true)
            notificationChannel.description = "Download complete!"

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    private fun createNotification() {
        notificationManager =
            ContextCompat.getSystemService(this, NotificationManager::class.java)
                    as NotificationManager

        val detailIntent = Intent(this, DetailActivity::class.java)
        detailIntent.putExtra("fileName", selectedDownloadUri?.title)
        detailIntent.putExtra("status", downloadStatus)

        pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(detailIntent)
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
        } as PendingIntent

        action = NotificationCompat.Action(
            R.drawable.ic_assistant_black_24dp,
            getString(R.string.notification_button),
            pendingIntent
        )

        val contentIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_assistant_black_24dp)
            .setContentTitle(selectedDownloadUri?.title)
            .setContentText(selectedDownloadUri?.text)
            .setContentIntent(contentPendingIntent)
            .addAction(action)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun download() {
        val request =
            DownloadManager.Request(Uri.parse(selectedDownloadUri?.uri))
                .setTitle(selectedDownloadUri?.title)
                .setDescription(getString(R.string.app_description))
                .setRequiresCharging(false)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "/repository.zip",
                )

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadID = downloadManager.enqueue(request)

        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.moveToFirst()) {
            when (cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) {
                DownloadManager.STATUS_FAILED -> {
                    downloadStatus = "Fail"
                    binding.customButton.buttonState = ButtonState.Completed
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadStatus = "Success"
                }
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
            else -> false
        }
    }

    companion object {
        private enum class URL(val uri: String, val title: String, val text: String) {
            GLIDE_URI(
                "https://github.com/bumptech/glide/archive/master.zip",
                "Glide: Image Loading Library By BumpTech",
                "Glide repository is downloaded"
            ),
            UDACITY_URI(
                "https://github.com/udacity/nd940-c3-advanced-android-programming-project-starter/archive/master.zip",
                "Udacity: Android Kotlin Nanodegree",
                "The Project 3 repository is downloaded"
            ),
            RETROFIT_URI(
                "https://github.com/square/retrofit/archive/master.zip",
                "Retrofit: Type-safe HTTP client by Square, Inc",
                "Retrofit repository is downloaded"
            ),
        }

        private const val CHANNEL_ID = "channelId"
        private const val NOTIFICATION_ID = 0
    }
}

