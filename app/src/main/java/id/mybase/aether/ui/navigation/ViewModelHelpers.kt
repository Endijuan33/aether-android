package id.mybase.aether.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Returns a [ViewModel] scoped to the host activity so it can be shared across navigation
 * destinations (e.g. the pairing dialog and the dashboard observe the same
 * [id.mybase.aether.ui.connection.ConnectionViewModel]).
 *
 * Instances are created with [creator] on first access and cleared with the activity, so no manual
 * lifecycle handling is needed.
 */
@Composable
inline fun <reified VM : ViewModel> activityViewModel(
    key: String? = null,
    crossinline creator: () -> VM,
): VM {
    val activity = LocalContext.current as ComponentActivity
    val factory =
        remember(key) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
            }
        }
    return viewModel(viewModelStoreOwner = activity, key = key, factory = factory)
}
