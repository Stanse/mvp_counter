package dev.repcounter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint

/**
 * M1 placeholder: real navigation between ExerciseListScreen/WorkoutScreen/SummaryScreen/
 * HistoryScreen (spec §11) is wired in M5 once `:feature:workout` has content.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RepCounterScaffold()
        }
    }
}

@Composable
private fun RepCounterScaffold() {
    MaterialTheme {
        Surface {
            Text(text = "RepCounter - M1 skeleton")
        }
    }
}
