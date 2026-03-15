package com.resonance.music.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.music.data.api.SubsonicApi
import com.resonance.music.data.api.SubsonicAuthInterceptor
import com.resonance.music.data.api.ServerCredentials
import com.resonance.music.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onServerUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "All fields are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            try {
                val serverUrl = state.serverUrl.trimEnd('/')

                // Test connection with a direct ping before saving
                val testApi = createTestApi(serverUrl, state.username, state.password)
                val response = testApi.ping()

                if (response.response.isOk) {
                    authRepository.saveCredentials(serverUrl, state.username, state.password)
                    _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                } else {
                    val error = response.response.error
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error?.message ?: "Authentication failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Connection failed: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun createTestApi(serverUrl: String, username: String, password: String): SubsonicApi {
        val interceptor = SubsonicAuthInterceptor {
            ServerCredentials(serverUrl, username, password)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl("$serverUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SubsonicApi::class.java)
    }
}
