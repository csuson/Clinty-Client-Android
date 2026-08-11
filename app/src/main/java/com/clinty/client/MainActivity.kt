package com.clinty.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clinty.client.services.InboxStore
import com.clinty.client.ui.navigation.ClintyNavHost
import com.clinty.client.ui.theme.ClintyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = InboxStore.getInstance(applicationContext)

        setContent {
            ClintyTheme {
                ClintyNavHost(store = store)
            }
        }
    }
}
