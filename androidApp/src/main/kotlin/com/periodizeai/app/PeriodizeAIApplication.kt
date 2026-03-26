package com.periodizeai.app

import android.app.Application
import com.periodizeai.app.di.initKoin
import org.koin.android.ext.koin.androidContext

class PeriodizeAIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@PeriodizeAIApplication)
        }
    }
}
