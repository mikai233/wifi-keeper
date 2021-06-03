package com.mdreamfever.wifi_keeper

import com.google.gson.annotations.SerializedName
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.Deferred
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

val schoolNetworkApi: SchoolNetworkApi by lazy {
    val client = OkHttpClient.Builder()
        .addInterceptor(BaseUrlInterceptor())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
    val retrofit = Retrofit.Builder()
        .baseUrl("http://172.18.254.13")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(CoroutineCallAdapterFactory())
        .build()
    retrofit.create(SchoolNetworkApi::class.java)
}

data class NetworkInfo(
    val info: String,
    @SerializedName("logout_domain")
    val logoutDomain: String,
    @SerializedName("logout_ip")
    val logoutIP: String,
    @SerializedName("logout_location")
    val logoutLocation: String,
    @SerializedName("logout_timer")
    val logoutTimer: Long,
    @SerializedName("logout_username")
    val logoutUsername: String,
    val status: Int,
) {
    override fun toString(): String {
        return "信息:${info} 认证域:${logoutDomain} 登录IP:${logoutIP} 登录位置:${logoutLocation} 在线时常:${
            showTimer(logoutTimer)
        }"
    }

    private fun showTimer(logoutTimer: Long): String {
        val seconds = logoutTimer % 60
        val minutes = logoutTimer / 60 % 60
        val hours = logoutTimer / 60 / 60 % 24
        val days = logoutTimer / 60 / 60 / 24
        return "${if (days.toInt() == 0) "" else "${days}天 "}${hours.toString().padStart(2, '0')}:${
            minutes.toString().padStart(2, '0')
        }:${seconds.toString().padStart(2, '0')}"
    }
}

data class LogoutInfo(
    val data: String,
    val info: String,
    val status: Int,
) {
    override fun toString(): String {
        return "结果:${data} 信息:${info} 状态:${status}"
    }
}

data class LoginInfo(
    val username: String,
    val domain: String,
    val password: String,
    @SerializedName("enablemacauth")
    val enableMacAuth: Int,
) {
    override fun toString(): String {
        return "用户名:${username} 认证域:${domain} 密码:${password} MAC认证:${enableMacAuth}"
    }
}

fun LoginInfo.toMap(): Map<String, String> {
    return mapOf(
        "username" to username,
        "domain" to domain,
        "password" to password,
        "enablemacauth" to enableMacAuth.toString()
    )
}

enum class Domain(
    val ISPName: String,
    val value: String,
    val enableMacAuth: Int
) {
    Teacher("教职工", " teacher ", 1),
    ChinaNet("中国电信", "ChinaNet", 0),
    Unicom("中国联通", "unicom", 0),
    CMCC("中国移动", "CMCC", 0),
}

interface SchoolNetworkApi {
    @GET("/index.php/index/init")
    fun getNetworkInfoAsync(@Query("_") timestamp: Long = System.currentTimeMillis()): Deferred<NetworkInfo>

    @POST("/index.php/index/logout")
    fun logoutAsync(): Deferred<LogoutInfo>

    @FormUrlEncoded
    @POST("/index.php/index/login")
    fun loginAsync(@FieldMap loginInfo: Map<String, String>): Deferred<NetworkInfo>
}