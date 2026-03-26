package com.periodizeai.app

import android.app.Application
import com.periodizeai.app.di.initKoin
import com.periodizeai.app.sync.WearOsSyncService
import com.periodizeai.app.widget.WidgetRefreshService
import org.koin.android.ext.koin.androidContext

class PeriodizeAIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetRefreshService.init(this)
        WearOsSyncService.init(this)
        initKoin {
            androidContext(this@PeriodizeAIApplication)
        }
    }
}
