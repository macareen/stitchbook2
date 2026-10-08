package com.macareen.stitchbook2.feature.ravelry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.ravelry.RavelryAuthException
import com.macareen.stitchbook2.domain.ravelry.RavelryCredentialStore
import com.macareen.stitchbook2.domain.ravelry.RavelryCredentials
import com.macareen.stitchbook2.domain.ravelry.RavelryImportPlan
import com.macareen.stitchbook2.domain.ravelry.RavelrySync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RavelryProblem { KEY_REJECTED, UNREACHABLE, SAVE_FAILED }

data class RavelryUiState(
    val hasKey: Boolean = false,
    val connectedAs: String? = null,
    val isWorking: Boolean = false,
    val problem: RavelryProblem? = null,
    val plan: RavelryImportPlan? = null,
    /** How many records the last approved pull saved; null until one runs. */
    val savedCount: Int? = null
)

/**
 * The Settings card's Ravelry connection: save or forget the key, check it,
 * preview a pull, and save what the person approves. Failures leave local
 * data untouched and say why.
 */
class RavelryViewModel(
    private val credentialStore: RavelryCredentialStore,
    private val sync: RavelrySync,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val state = MutableStateFlow(RavelryUiState())
    val uiState: StateFlow<RavelryUiState> = state.asStateFlow()

    init {
        scope.launch {
            val hasKey = withContext(ioDispatcher) { credentialStore.load() != null }
            state.update { it.copy(hasKey = hasKey) }
        }
    }

    fun saveKey(accessKey: String, personalKey: String) {
        val credentials = runCatching { RavelryCredentials(accessKey.trim(), personalKey.trim()) }.getOrNull() ?: return
        run {
            withContext(ioDispatcher) { credentialStore.save(credentials) }
            state.update { it.copy(hasKey = true) }
            val username = sync.connectedUsername()
            state.update { it.copy(connectedAs = username) }
        }
    }

    fun forgetKey() {
        scope.launch {
            withContext(ioDispatcher) { credentialStore.clear() }
            state.value = RavelryUiState()
        }
    }

    fun preview() = run {
        val plan = sync.preview()
        state.update { it.copy(plan = plan, savedCount = null) }
    }

    fun apply(includeChanges: Boolean) {
        val plan = state.value.plan ?: return
        run {
            val saved = sync.apply(plan, includeChanges)
            state.update { it.copy(plan = null, savedCount = saved) }
        }
    }

    fun dismissPlan() = state.update { it.copy(plan = null) }

    private fun run(block: suspend () -> Unit) {
        if (state.value.isWorking) return
        state.update { it.copy(isWorking = true, problem = null) }
        scope.launch {
            val problem = try {
                block()
                null
            } catch (error: CancellationException) {
                throw error
            } catch (_: RavelryAuthException) {
                RavelryProblem.KEY_REJECTED
            } catch (_: java.io.IOException) {
                RavelryProblem.UNREACHABLE
            } catch (_: Exception) {
                RavelryProblem.SAVE_FAILED
            }
            state.update { it.copy(isWorking = false, problem = problem) }
        }
    }

    companion object {
        fun factory(credentialStore: RavelryCredentialStore, sync: RavelrySync): ViewModelProvider.Factory =
            viewModelFactory { initializer { RavelryViewModel(credentialStore, sync) } }
    }
}
