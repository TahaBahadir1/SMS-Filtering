package com.smsfiltering.client.data.remote

/**
 * Ağ istekleri sonucunu güvenli ve tipli bir şekilde yönetmek için sealed class.
 */
sealed class NetworkResult<out T> {

    data class Success<out T>(val data: T) : NetworkResult<T>()

    data class Error(
        val code: Int,
        val message: String
    ) : NetworkResult<Nothing>()

    data class Exception(val throwable: Throwable) : NetworkResult<Nothing>()

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    inline fun onSuccess(action: (value: T) -> Unit): NetworkResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (code: Int, message: String) -> Unit): NetworkResult<T> {
        if (this is Error) action(code, message)
        return this
    }

    inline fun onException(action: (throwable: Throwable) -> Unit): NetworkResult<T> {
        if (this is Exception) action(throwable)
        return this
    }
}
