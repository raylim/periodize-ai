package com.periodizeai.app

import androidx.compose.ui.window.ComposeUIViewController
import com.periodizeai.app.di.initKoin
import com.periodizeai.app.ui.App

fun MainViewController() = ComposeUIViewController(
    configure = { initKoin() }
) {
    App()
}
