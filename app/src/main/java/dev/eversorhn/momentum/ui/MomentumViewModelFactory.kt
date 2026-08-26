package dev.eversorhn.momentum.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.eversorhn.momentum.MomentumApplication
import dev.eversorhn.momentum.data.repository.MomentumRepository

/**
 * Tries a (MomentumRepository, Context) constructor first, falls back to (MomentumRepository).
 * Keeps ViewModels that don't need Context (Naming, Forecast) from having to fake one.
 */
class MomentumViewModelFactory(
    private val repository: MomentumRepository,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return try {
            modelClass.getConstructor(MomentumRepository::class.java, Context::class.java)
                .newInstance(repository, appContext) as T
        } catch (e: NoSuchMethodException) {
            modelClass.getConstructor(MomentumRepository::class.java).newInstance(repository) as T
        }
    }
}

@Composable
inline fun <reified T : ViewModel> momentumViewModel(): T {
    val app = LocalContext.current.applicationContext as MomentumApplication
    return viewModel(factory = MomentumViewModelFactory(app.repository, app))
}
