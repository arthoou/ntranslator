package com.arthou.ntranslator.util

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.http.HttpHeaders
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import com.arthou.ntranslator.NTranslator
import java.io.BufferedOutputStream
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
    private var hasApache: Boolean? = null

    // Um cliente unico e reaproveitado: abrir uma conexao TLS nova por traducao
    // e lento e faz o Google marcar o cliente como abusivo mais rapido.
    private val apacheClient: CloseableHttpClient by lazy {
        HttpClients.custom()
            .setMaxConnTotal(16)
            .setMaxConnPerRoute(8)
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectTimeout(30_000)
                    .setSocketTimeout(30_000)
                    .setCookieSpec(CookieSpecs.STANDARD)
                    .setConnectionRequestTimeout(30_000)
                    .build()
            )
            .build()
    }

    fun get(uri: String, headers: Map<String, String> = mapOf()): JsonElement {
        ensureHttpBackend()

        return if (hasApache == true) {
            getApache(uri, headers)
        } else {
            getJava(uri, headers)
        }
    }

    fun post(uri: String, body: JsonObject, headers: Map<String, String> = mapOf()): JsonElement {
        ensureHttpBackend()

        return if (hasApache == true) {
            postApache(uri, body, headers)
        } else {
            postJava(uri, body, headers)
        }
    }

    private fun ensureHttpBackend() {
        if (hasApache == null) {
            try {
                // This is literally only here because of Forge.
                Class.forName("org.apache.http.impl.client.HttpClients")
                Class.forName("org.apache.http.HttpHeaders")
                Class.forName("org.apache.http.util.EntityUtils")
                Class.forName("org.apache.commons.logging.LogFactory")
                hasApache = true
            } catch (e: Throwable) {
                NTranslator.logger.error("Detected that Apache HTTP support is unavailable! Translations may fail after a certain period of time.")
                NTranslator.logger.error("For more information, view: https://github.com/arthou/NTranslator/issues/4")
                e.printStackTrace()
                hasApache = false
            }
        }
    }

    fun getJava(uri: String, headers: Map<String, String> = mapOf()): JsonElement {
        val url = URL(uri)
        var connection: HttpURLConnection? = null

        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true

            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            val code = connection.responseCode
            if (code / 100 != 2) {
                connection.errorStream?.close()
                throw HttpStatusException(code, "Failed to load $uri (code: $code)")
            }

            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
        } finally {
            connection?.disconnect()
        }
    }

    fun postJava(uri: String, body: JsonObject, headers: Map<String, String> = mapOf()): JsonElement {
        val url = URL(uri)
        var connection: HttpURLConnection? = null

        val data = body.toString().toByteArray(Charsets.UTF_8)

        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            connection.doOutput = true
            connection.setFixedLengthStreamingMode(data.size)

            BufferedOutputStream(connection.outputStream).use {
                it.write(data)
                it.flush()
            }

            val code = connection.responseCode
            if (code / 100 != 2) {
                connection.errorStream?.close()
                throw HttpStatusException(code, "Failed to load $uri (code: $code)")
            }

            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
        } finally {
            connection?.disconnect()
        }
    }

    fun getApache(uri: String, headers: Map<String, String> = mapOf()): JsonElement {
        val request = HttpGet(uri)

        request.setHeader(HttpHeaders.ACCEPT, "application/json")
        for ((key, value) in headers) {
            request.setHeader(key, value)
        }

        apacheClient.execute(request).use { response ->
            val code = response.statusLine.statusCode
            val responseBody = response.entity?.let { EntityUtils.toString(it, Charsets.UTF_8) }.orEmpty()

            if (code / 100 != 2) {
                throw HttpStatusException(code, "Failed to load $uri (code: $code)")
            }

            return JsonParser.parseString(responseBody)
        }
    }

    fun postApache(uri: String, body: JsonObject, headers: Map<String, String> = mapOf()): JsonElement {
        val request = HttpPost(uri)

        request.setHeader(HttpHeaders.ACCEPT, "application/json")
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
        for ((key, value) in headers) {
            request.setHeader(key, value)
        }

        request.entity = StringEntity(body.toString(), Charsets.UTF_8)

        apacheClient.execute(request).use { response ->
            val code = response.statusLine.statusCode
            val responseBody = response.entity?.let { EntityUtils.toString(it, Charsets.UTF_8) }.orEmpty()

            if (code / 100 != 2) {
                throw HttpStatusException(code, "Failed to load $uri (code: $code)")
            }

            return JsonParser.parseString(responseBody)
        }
    }
}
