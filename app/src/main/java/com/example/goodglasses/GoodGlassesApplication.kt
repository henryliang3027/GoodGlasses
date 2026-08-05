package com.example.goodglasses

import android.app.Application
import android.util.Log
import com.meta.wearable.dat.core.Wearables

class GoodGlassesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Wearables.initialize(this).onFailure { error, _ ->
            Log.e("GoodGlassesApplication", "Failed to initialize DAT: ${error.description}")
        }
    }
}
