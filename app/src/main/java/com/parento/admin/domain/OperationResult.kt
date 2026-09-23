package com.parento.admin.domain

sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>
    data class Failure(val error: AdminError) : OperationResult<Nothing>
}
