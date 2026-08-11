package com.clinty.client.services

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.URI
import java.net.URL

object DeploymentURLNormalizer {
    private val apiPathSuffixes = listOf("/threads/search", "/threads", "/info")

    /// Normalizes a user-entered deployment URL for storage (strips accidental API paths).
    fun normalize(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed

        if (!trimmed.contains("://")) {
            trimmed = "https://$trimmed"
        }

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed.trimEnd('/')

        var path = uri.path.orEmpty()
        for (suffix in apiPathSuffixes) {
            if (path.lowercase().endsWith(suffix.lowercase())) {
                path = path.dropLast(suffix.length)
                break
            }
        }
        if (path == "/") path = ""

        return buildNormalizedUrl(
            scheme = uri.scheme ?: "https",
            host = uri.host.orEmpty(),
            port = uri.port,
            path = path.trimEnd('/'),
        ).trimEnd('/')
    }

    fun shouldUpgradeHttpToHttps(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val lower = host.lowercase()
        if (lower == "localhost" || lower == "127.0.0.1") return false
        if (lower.startsWith("192.168.") || lower.startsWith("10.") || lower.startsWith("172.")) return false
        return true
    }

    fun endpointUrl(base: URL, path: String): URL {
        val normalizedBase = normalizeRequestUrl(base) ?: base
        val segments = path.split('/').filter { it.isNotEmpty() }
        val basePath = normalizedBase.path.trim('/').split('/').filter { it.isNotEmpty() }
        val combined = (basePath + segments).joinToString("/")
        val urlString = buildString {
            append(normalizedBase.protocol)
            append("://")
            append(normalizedBase.host)
            if (normalizedBase.port > 0 &&
                !((normalizedBase.protocol == "https" && normalizedBase.port == 443) ||
                    (normalizedBase.protocol == "http" && normalizedBase.port == 80))
            ) {
                append(':')
                append(normalizedBase.port)
            }
            if (combined.isNotEmpty()) {
                append('/')
                append(combined)
            }
        }
        return normalizeRequestUrl(URL(urlString)) ?: URL(urlString)
    }

    /// Normalizes a request URL without stripping API path segments.
    fun normalizeRequestUrl(url: URL): URL? {
        val uri = runCatching { URI(url.toString()) }.getOrNull() ?: return url

        var host = uri.host.orEmpty()
        if (host.lowercase().startsWith("www.")) {
            host = host.drop(4)
        }

        var scheme = uri.scheme ?: "https"
        var port = uri.port
        if (scheme == "http" && shouldUpgradeHttpToHttps(host)) {
            scheme = "https"
            if (port == 80) port = -1
        }

        var path = uri.path.orEmpty()
        if (path.endsWith("/") && path != "/") {
            path = path.dropLast(1)
        }
        if (path == "/") path = ""

        val normalized = buildNormalizedUrl(scheme, host, port, path)
        return runCatching { URL(normalized) }.getOrNull()
    }

    fun prepareRequest(request: Request): Request {
        val normalized = normalizeRequestUrl(URL(request.url.toString())) ?: return request
        val httpUrl = normalized.toString().toHttpUrlOrNull() ?: return request

        val builder = request.newBuilder().url(httpUrl)
        builder.header("Host", httpUrl.host)
        if (request.method == "POST") {
            builder.header("Content-Type", "application/json")
        }
        return builder.build()
    }

    private fun buildNormalizedUrl(scheme: String, host: String, port: Int, path: String): String {
        var normalizedScheme = scheme
        var normalizedPort = port
        if (normalizedScheme == "http" && shouldUpgradeHttpToHttps(host)) {
            normalizedScheme = "https"
            if (normalizedPort == 80) normalizedPort = -1
        }

        var normalizedHost = host
        if (normalizedHost.lowercase().startsWith("www.")) {
            normalizedHost = normalizedHost.drop(4)
        }

        val portPart = when {
            normalizedPort <= 0 -> ""
            (normalizedScheme == "https" && normalizedPort == 443) ||
                (normalizedScheme == "http" && normalizedPort == 80) -> ""
            else -> ":$normalizedPort"
        }

        return buildString {
            append(normalizedScheme)
            append("://")
            append(normalizedHost)
            append(portPart)
            if (path.isNotEmpty()) {
                append('/')
                append(path.trimStart('/'))
            }
        }
    }
}
