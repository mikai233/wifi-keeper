package com.mdreamfever.wifi_keeper

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class BaseUrlInterceptor : Interceptor {
    private val tag = BaseUrlInterceptor::class.java.name
    private val originHost = "172.18.254.13"
    private val backHost = "172.18.254.14"
    override fun intercept(chain: Interceptor.Chain): Response {
        if (Keeper.isConnectTimeOut) {
            val request = chain.request()
            val url = request.url()
            Log.i(tag, "connect timeout at host ${url.host()}")
            val newUrl = if (url.host() == originHost) {
                url.newBuilder()
                    .host(backHost)
                    .build()
            } else {
                url.newBuilder()
                    .host(originHost)
                    .build()
            }
            Keeper.isConnectTimeOut = false
            return chain.proceed(request.newBuilder().url(newUrl).build())
        } else {
            return chain.proceed(chain.request())
        }
    }
}