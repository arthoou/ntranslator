package com.arthou.ntranslator.util

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL

/**
 * Erro de resposta HTTP com o codigo preservado, para que quem chamou possa
 * distinguir um 429 (limite de requisicoes, vale tentar de novo) de um 400.
 */
class HttpStatusException(val code: Int, message: String) : Exception(message) {
    val isRetryable: Boolean
        get() = code == 408 || code == 425 || code == 429 || code / 100 == 5
}

object HttpHelper {
    fun get(uri: String, headers: Map<String, String> = mapOf()): JsonElement {
        val url = URL(uri)
        var connection: HttpURLConnection? = null

        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true

            connection.setRequestProperty("Accept", "*/*")
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            val code = connection.responseCode
            if (code / 100 != 2) {
                connection.errorStream?.close()
                throw HttpStatusException(code, "Failed to load $uri (code: $code)")
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JsonParser.parseString(body)
        } finally {
            connection?.disconnect()
        }
    }
}
