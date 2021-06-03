package com.mdreamfever.wifi_keeper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.SocketTimeoutException
import kotlin.coroutines.CoroutineContext

typealias logCallback = (Any?) -> Unit

class Keeper : Service(), CoroutineScope {
    private val tag = Keeper::class.java.name
    private lateinit var job: Job
    private lateinit var loginInfo: LoginInfo
    private val serviceBinder = ServiceBinder()
    private lateinit var callback: logCallback
    private var lastLoginAt: Long = 0L

    companion object {
        var isConnectTimeOut = false
    }

    inner class ServiceBinder : Binder() {
        fun login(reLoginInfo: LoginInfo) {
            if (System.currentTimeMillis() - lastLoginAt < 3000) {
                callback("请勿频繁登录")
                return
            }
            loginInfo = reLoginInfo
            if (this@Keeper::job.isInitialized) {
                job.cancel()
            }
            job = superviseNetwork()
            lastLoginAt = System.currentTimeMillis()
        }

        suspend fun logout(): LogoutInfo? {
            if (this@Keeper::job.isInitialized) {
                job.cancel()
            }
            return withContext(Dispatchers.IO) {
                try {
                    schoolNetworkApi.logoutAsync().await().also(callback)
                } catch (e: Exception) {
                    callback(e.message)
                    Log.e(tag, "登出错误")
                    null
                }
            }
        }

        fun registerCallback(logCallback: logCallback) {
            callback = logCallback
        }
    }

    private fun superviseNetwork(): Job {
        return launch {
            withContext(Dispatchers.IO) {
                while (true) {
                    if (getConnectionType(this@Keeper) != 2) {
                        callback("非WIFI网络")
                        delay(10 * 1000)
                        continue
                    }
                    try {
                        schoolNetworkApi.getNetworkInfoAsync().await().also { info ->
                            Log.i(tag, info.toString())
                            callback(info)
                            info.status.takeIf { it == 0 }?.let {
                                Log.i(tag, "login $loginInfo")
                                callback(loginInfo)
                                schoolNetworkApi.loginAsync(loginInfo.toMap()).await().also {
                                    Log.i(tag, it.toString())
                                    callback(info)
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        isConnectTimeOut = true
                    } catch (e: Exception) {
                        Log.e(tag, e.message ?: "an error occured")
                        callback(e.message)
                    } finally {
                        delay(10 * 1000)
                    }
                }
            }
        }
    }

    private fun getConnectionType(context: Context): Int {
        var result = 0 // Returns connection type. 0: none; 1: mobile data; 2: wifi
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm?.run {
                cm.getNetworkCapabilities(cm.activeNetwork)?.run {
                    when {
                        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                            result = 2
                        }
                        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                            result = 1
                        }
                        hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                            result = 3
                        }
                    }
                }
            }
        } else {
            cm?.run {
                cm.activeNetworkInfo?.run {
                    when (type) {
                        ConnectivityManager.TYPE_WIFI -> {
                            result = 2
                        }
                        ConnectivityManager.TYPE_MOBILE -> {
                            result = 1
                        }
                        ConnectivityManager.TYPE_VPN -> {
                            result = 3
                        }
                    }
                }
            }
        }
        return result
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "wifi_keeper",
                "WIFI Keeper monitor service",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, 0)
        val notification = NotificationCompat.Builder(this, "wifi_keeper")
            .setContentTitle("WIFI Keeper")
            .setContentText("请不要杀死此程序")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentIntent(pi)
            .build()
        startForeground(1, notification)
    }

    override fun onBind(intent: Intent): IBinder {
        return serviceBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override val coroutineContext: CoroutineContext
        get() = CoroutineName("keepService")
}