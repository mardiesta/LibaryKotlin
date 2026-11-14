package com.example.libary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.libary.data.LibraryGraph
import com.example.libary.ui.LibraryRoot
import com.example.libary.ui.theme.LibaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = LibraryGraph.repository(this)
        setContent {
            LibaryTheme {
                LibraryRoot(repository)
            }
        }
    }
}
