package com.spendsense.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spendsense.app.ui.navigation.SpendSenseNavHost
import com.spendsense.app.ui.theme.SpendSenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SpendSenseTheme {
                SpendSenseNavHost()
            }
        }
    }
}
