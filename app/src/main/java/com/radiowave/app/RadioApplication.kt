package com.radiowave.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.Call
import okhttp3.Request

class RadioApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        RadioNetwork.initialize(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .callFactory(object : Call.Factory {
            override fun newCall(request: Request): Call = RadioNetwork.client().newCall(request)
        }).build()
}
