package id.mybase.aether.ui.pairing

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun DeveloperOptionsStatus() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(DeveloperOptionsState.UNAVAILABLE) }
    LifecycleResumeEffect(context) {
        state = developerOptionsState(context)
        onPauseOrDispose {}
    }
    Text(
        when (state) {
            DeveloperOptionsState.ENABLED -> "Developer options: enabled"
            DeveloperOptionsState.DISABLED ->
                "Developer options: disabled. Enable manually in Settings → About phone → Build number (or OS version)."
            DeveloperOptionsState.UNAVAILABLE ->
                "Developer options state unavailable. Check Android Settings."
        },
        style = MaterialTheme.typography.bodySmall,
    )
}
