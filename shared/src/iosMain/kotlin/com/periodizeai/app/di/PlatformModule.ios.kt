package com.periodizeai.app.di

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.periodizeai.app.database.PeriodizeAIDatabase
import org.koin.dsl.module

actual val platformModule = module {
    single {
        NativeSqliteDriver(
            schema   = PeriodizeAIDatabase.Schema,
            name     = "periodizeai.db",
        )
    }
    single { PeriodizeAIDatabase(get()) }
}
