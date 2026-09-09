package com.lirui.charchat.data.remote

/** 网络/接口层统一异常，便于 UI 区分展示。 */
class ApiException(override val message: String, cause: Throwable? = null) : Exception(message, cause)
