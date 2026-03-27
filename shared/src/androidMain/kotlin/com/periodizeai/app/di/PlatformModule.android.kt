package com.periodizeai.app.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.periodizeai.app.database.PeriodizeAIDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema  = PeriodizeAIDatabase.Schema,
            context = androidContext(),
            name    = "periodizeai.db",
        )
    }
    single { PeriodizeAIDatabase(get()) }
}
