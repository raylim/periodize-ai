package com.periodizeai.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.periodizeai.app.wear.data.WearDataStore
import com.periodizeai.app.wear.ui.WearApp
import com.periodizeai.app.wear.ui.theme.WearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearDataStore.init(this)
        setContent {
            WearTheme {
                WearApp(dataStore = WearDataStore)
            }
        }
    }
}
