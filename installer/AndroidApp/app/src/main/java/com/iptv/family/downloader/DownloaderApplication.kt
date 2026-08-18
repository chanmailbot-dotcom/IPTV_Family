package com.iptv.family.downloader

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class DownloaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
    }
}

class AppLifecycleObserver : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        // App en foreground
    }
    
    override fun onStop(owner: LifecycleOwner) {
        // App en background
    }
}