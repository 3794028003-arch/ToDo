package com.example.localfirst.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.localfirst.board.BoardRoute
import com.example.localfirst.board.BoardViewModel
import com.example.localfirst.data.TaskRepository

class MainActivity : ComponentActivity() {
    private val boardViewModel: BoardViewModel by viewModels {
        BoardViewModelFactory(
            repository = (application as LocalFirstApplication).graph.repository,
        )
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2F6B55),
                    secondary = Color(0xFF53665E),
                    surfaceVariant = Color(0xFFE8F0EB),
                ),
            ) {
                BoardRoute(viewModel = boardViewModel)
            }
        }
    }
}

private class BoardViewModelFactory(
    private val repository: TaskRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BoardViewModel::class.java))
        return BoardViewModel(repository) as T
    }
}
