package org.a2ui.compose.error

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

sealed class A2UIError {
    data class ParseError(val message: String, val rawMessage: String? = null) : A2UIError()
    data class NetworkError(val message: String, val cause: Throwable? = null) : A2UIError()
    data class ComponentError(val componentId: String, val message: String) : A2UIError()
    data class ValidationError(val fieldId: String, val message: String) : A2UIError()
    data class StateError(val surfaceId: String, val message: String) : A2UIError()
    data class UnknownError(val message: String, val cause: Throwable? = null) : A2UIError()
}

enum class ErrorSeverity {
    ERROR,
    WARNING,
    INFO
}

data class ErrorInfo(
    val error: A2UIError,
    val severity: ErrorSeverity = ErrorSeverity.ERROR,
    val timestamp: Long = System.currentTimeMillis(),
    val recoverable: Boolean = false,
    val recoveryAction: (() -> Unit)? = null
)

interface A2UIErrorHandler {
    fun handleError(error: A2UIError, severity: ErrorSeverity = ErrorSeverity.ERROR)
    fun showError(message: String, severity: ErrorSeverity = ErrorSeverity.ERROR)
    fun clearErrors()
    fun getErrors(): List<ErrorInfo>
}

class DefaultErrorHandler : A2UIErrorHandler {
    private val _errors = mutableStateListOf<ErrorInfo>()
    val errors: List<ErrorInfo> get() = _errors.toList()

    override fun handleError(error: A2UIError, severity: ErrorSeverity) {
        val errorInfo = ErrorInfo(
            error = error,
            severity = severity,
            recoverable = isRecoverable(error),
            recoveryAction = getRecoveryAction(error)
        )
        _errors.add(errorInfo)
    }

    override fun showError(message: String, severity: ErrorSeverity) {
        handleError(A2UIError.UnknownError(message), severity)
    }

    override fun clearErrors() {
        _errors.clear()
    }

    override fun getErrors(): List<ErrorInfo> {
        return _errors.toList()
    }

    private fun isRecoverable(error: A2UIError): Boolean {
        return when (error) {
            is A2UIError.NetworkError -> true
            is A2UIError.ParseError -> false
            is A2UIError.ComponentError -> true
            is A2UIError.ValidationError -> true
            is A2UIError.StateError -> true
            is A2UIError.UnknownError -> false
        }
    }

    private fun getRecoveryAction(error: A2UIError): (() -> Unit)? {
        return when (error) {
            is A2UIError.NetworkError -> {
                { /* Retry network request */ }
            }
            else -> null
        }
    }

    fun dismissError(index: Int) {
        if (index in _errors.indices) {
            _errors.removeAt(index)
        }
    }
}

@Composable
fun ErrorBanner(
    errorInfo: ErrorInfo,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (icon, containerColor, contentColor) = when (errorInfo.severity) {
        ErrorSeverity.ERROR -> Triple(
            Icons.Default.Error,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        ErrorSeverity.WARNING -> Triple(
            Icons.Default.Warning,
            Color(0xFFFFF3E0),
            Color(0xFFE65100)
        )
        ErrorSeverity.INFO -> Triple(
            Icons.Default.Info,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getErrorMessage(errorInfo.error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
            
            Row {
                if (errorInfo.recoverable && onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Text("Retry", color = contentColor)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorScreen(
    error: A2UIError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = getErrorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}

fun getErrorMessage(error: A2UIError): String {
    return when (error) {
        is A2UIError.ParseError -> "Failed to parse message: ${error.message}"
        is A2UIError.NetworkError -> "Network error: ${error.message}"
        is A2UIError.ComponentError -> "Component error (${error.componentId}): ${error.message}"
        is A2UIError.ValidationError -> "Validation error: ${error.message}"
        is A2UIError.StateError -> "State error: ${error.message}"
        is A2UIError.UnknownError -> error.message
    }
}

@Composable
fun rememberErrorHandler(): DefaultErrorHandler {
    return remember { DefaultErrorHandler() }
}
