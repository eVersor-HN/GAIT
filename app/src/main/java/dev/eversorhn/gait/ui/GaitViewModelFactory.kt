package dev.eversorhn.gait.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.eversorhn.gait.GaitApplication
import dev.eversorhn.gait.data.repository.GaitRepository

/**
 * Tries a (GaitRepository, Context) constructor first, falls back to (GaitRepository).
 * Keeps ViewModels that don't need Context (Naming, Forecast) from having to fake one.
 */
class GaitViewModelFactory(
    private val repository: GaitRepository,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return try {
            modelClass.getConstructor(GaitRepository::class.java, Context::class.java)
                .newInstance(repository, appContext) as T
        } catch (e: NoSuchMethodException) {
            modelClass.getConstructor(GaitRepository::class.java).newInstance(repository) as T
        }
    }
}

@Composable
inline fun <reified T : ViewModel> gaitViewModel(): T {
    val app = LocalContext.current.applicationContext as GaitApplication
    return viewModel(factory = GaitViewModelFactory(app.repository, app))
}
