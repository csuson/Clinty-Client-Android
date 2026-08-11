package com.clinty.client.services

import com.clinty.client.models.AgentInbox
import com.clinty.client.models.AppJson
import com.clinty.client.models.DeploymentInfoResponse
import com.clinty.client.models.HumanResponse
import com.clinty.client.models.LangGraphThreadSummary
import com.clinty.client.models.RunCommand
import com.clinty.client.models.RunResumeRequest
import com.clinty.client.models.ThreadData
import com.clinty.client.models.ThreadSearchRequest
import com.clinty.client.models.ThreadStateResponse
import com.clinty.client.models.UpdateThreadStateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.net.URL
import java.util.concurrent.TimeUnit

class LangGraphClient(private val store: InboxStore) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    sealed class LangGraphClientError(message: String) : Exception(message) {
        data object InvalidURL : LangGraphClientError("Deployment URL is invalid.")
        data object NoInboxSelected : LangGraphClientError("Add and select an inbox in Settings.")
        data class HttpError(val statusCode: Int, val body: String, val requestUrl: String, val method: String) :
            LangGraphClientError(formatHttpError(statusCode, body, requestUrl, method))
        data class DecodingFailed(val detail: String) :
            LangGraphClientError("Failed to decode server response: $detail")
        data class EmptyResponse(val requestUrl: String, val method: String) :
            LangGraphClientError("Empty response body from $method $requestUrl")

        companion object {
            private fun formatHttpError(
                statusCode: Int,
                body: String,
                requestUrl: String,
                method: String,
            ): String {
                if (statusCode == 405) {
                    return "HTTP 405 on $method $requestUrl. The LangGraph API requires POST to /threads/search. " +
                        "Set deployment URL to https://clinty.net (no www, no /threads/search path). " +
                        "If this persists, check nginx routes www to a different server."
                }
                return "HTTP $statusCode $method $requestUrl: ${httpErrorSnippet(body)}"
            }

            private fun httpErrorSnippet(body: String): String {
                val trimmed = body.trim()
                if (!trimmed.contains("<html", ignoreCase = true)) {
                    return trimmed.ifBlank { "(empty body)" }
                }
                val titleRegex = Regex("(?is)<title>(.*?)</title>")
                return titleRegex.find(trimmed)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
                    ?: "HTML error response"
            }
        }
    }

    suspend fun searchThreads(offset: Int = 0, limit: Int = 25): List<ThreadData> =
        withContext(Dispatchers.IO) {
            val inbox = selectedInbox()
            searchThreads(offset, minOf(limit, 100), inbox)
                .filter { it.status == "interrupted" }
                .sortedWith(compareByDescending { it.thread.createdDate })
        }

    suspend fun deleteThread(threadId: String) = withContext(Dispatchers.IO) {
        val inbox = selectedInbox()
        val baseUrl = store.normalizedBaseUrl(inbox)
        val request = Request.Builder()
            .url(DeploymentURLNormalizer.endpointUrl(baseUrl, "threads/$threadId"))
            .delete()
            .applyAuth(inbox)
            .build()
        executeVoid(request)
    }

    suspend fun fetchThread(threadId: String): ThreadData = withContext(Dispatchers.IO) {
        val inbox = selectedInbox()
        val baseUrl = store.normalizedBaseUrl(inbox)
        val request = Request.Builder()
            .url(DeploymentURLNormalizer.endpointUrl(baseUrl, "threads/$threadId"))
            .get()
            .applyAuth(inbox)
            .build()

        val thread: LangGraphThreadSummary = perform(request)

        if (thread.status == "interrupted") {
            InterruptParser.processInterruptedThread(thread)?.let { return@withContext it }
            val state = getThreadState(threadId, inbox)
            return@withContext ThreadData(
                thread = thread,
                status = "interrupted",
                interrupts = InterruptParser.interruptsFromState(state)
                    ?: InterruptParser.interruptsFromThread(thread),
                invalidSchema = false,
            )
        }

        ThreadData(thread = thread, status = thread.status, interrupts = null, invalidSchema = false)
    }

    suspend fun sendHumanResponse(threadId: String, responses: List<HumanResponse>) =
        withContext(Dispatchers.IO) {
            val inbox = selectedInbox()
            val baseUrl = store.normalizedBaseUrl(inbox)
            val body = RunResumeRequest(
                assistantId = inbox.graphId,
                command = RunCommand(resume = responses),
                streamMode = "events",
            )
            val request = Request.Builder()
                .url(DeploymentURLNormalizer.endpointUrl(baseUrl, "threads/$threadId/runs"))
                .post(AppJson.instance.encodeToString(body).toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .applyAuth(inbox)
                .build()
            executeVoid(request)
        }

    suspend fun resolveThread(threadId: String) = withContext(Dispatchers.IO) {
        val inbox = selectedInbox()
        val baseUrl = store.normalizedBaseUrl(inbox)
        val request = Request.Builder()
            .url(DeploymentURLNormalizer.endpointUrl(baseUrl, "threads/$threadId/state"))
            .post(AppJson.instance.encodeToString(UpdateThreadStateRequest.resolve).toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .applyAuth(inbox)
            .build()
        executeVoid(request)
    }

    suspend fun fetchThreadState(threadId: String): ThreadStateResponse =
        withContext(Dispatchers.IO) {
            getThreadState(threadId, selectedInbox())
        }

    suspend fun fetchDeploymentInfo(baseUrl: String): DeploymentInfoResponse? =
        withContext(Dispatchers.IO) {
            val normalized = DeploymentURLNormalizer.normalize(baseUrl)
            val url = runCatching { URL(normalized) }.getOrNull() ?: return@withContext null
            val request = Request.Builder()
                .url(DeploymentURLNormalizer.endpointUrl(url, "info"))
                .get()
                .applyAuth()
                .build()
            runCatching { perform<DeploymentInfoResponse>(request) }.getOrNull()
        }

    private suspend fun searchThreads(
        offset: Int,
        limit: Int,
        inbox: AgentInbox,
    ): List<ThreadData> {
        val baseUrl = store.normalizedBaseUrl(inbox)
        val body = ThreadSearchRequest(offset = offset, limit = limit, graphId = inbox.graphId)
        val request = Request.Builder()
            .url(DeploymentURLNormalizer.endpointUrl(baseUrl, "threads/search"))
            .post(AppJson.instance.encodeToString(body).toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .applyAuth(inbox)
            .build()

        val threads = performThreadSearch(request)
        val results = mutableListOf<ThreadData>()

        for (thread in threads) {
            if (thread.status != "interrupted") continue

            val processed = InterruptParser.processInterruptedThread(thread)
            if (processed != null && !processed.interrupts.isNullOrEmpty()) {
                results.add(processed)
                continue
            }

            val interruptsFromSummary = InterruptParser.interruptsFromThread(thread)
            if (!interruptsFromSummary.isNullOrEmpty()) {
                results.add(
                    ThreadData(
                        thread = thread,
                        status = "interrupted",
                        interrupts = interruptsFromSummary,
                        invalidSchema = interruptsFromSummary.any {
                            it.actionRequest.action == com.clinty.client.models.AgentInboxConstants.IMPROPER_SCHEMA
                        },
                    ),
                )
                continue
            }

            val state = getThreadState(thread.threadId, inbox)
            val interrupts = InterruptParser.interruptsFromState(state)
            results.add(
                ThreadData(
                    thread = thread,
                    status = "interrupted",
                    interrupts = interrupts,
                    invalidSchema = interrupts == null,
                ),
            )
        }

        return results
    }

    private suspend fun getThreadState(threadId: String, inbox: AgentInbox): ThreadStateResponse {
        val baseUrl = store.normalizedBaseUrl(inbox)
        val request = Request.Builder()
            .url(DeploymentURLNormalizer.endpointUrl(baseUrl, "threads/$threadId/state"))
            .get()
            .applyAuth(inbox)
            .build()
        return perform(request)
    }

    private fun selectedInbox(): AgentInbox {
        return store.selectedInbox ?: throw LangGraphClientError.NoInboxSelected
    }

    private fun Request.Builder.applyAuth(inbox: AgentInbox): Request.Builder {
        val clintKey = store.clintAPIKey.value.trim()
        if (clintKey.isNotEmpty()) {
            header("X-Api-Key", clintKey)
            return this
        }

        val langsmithKey = store.langsmithAPIKey.value.trim()
        if (store.requiresAPIKey(inbox) && langsmithKey.isEmpty()) return this
        if (langsmithKey.isNotEmpty()) {
            header("X-Api-Key", langsmithKey)
        }
        return this
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        val clintKey = store.clintAPIKey.value.trim()
        if (clintKey.isNotEmpty()) {
            header("X-Api-Key", clintKey)
            return this
        }

        val langsmithKey = store.langsmithAPIKey.value.trim()
        if (langsmithKey.isNotEmpty()) {
            header("X-Api-Key", langsmithKey)
        }
        return this
    }

    private fun performThreadSearch(request: Request): List<LangGraphThreadSummary> {
        val (body, response) = readResponse(request)
        validate(response.code, body, request)
        if (body.isBlank()) {
            throw LangGraphClientError.EmptyResponse(request.url.toString(), request.method)
        }
        return try {
            AppJson.instance.decodeFromString(
                ListSerializer(LangGraphThreadSummary.serializer()),
                body,
            )
        } catch (e: Exception) {
            throw LangGraphClientError.DecodingFailed(e.message ?: e.toString())
        }
    }

    private inline fun <reified T> perform(request: Request): T {
        val (body, response) = readResponse(request)
        validate(response.code, body, request)
        if (body.isBlank()) {
            throw LangGraphClientError.EmptyResponse(request.url.toString(), request.method)
        }
        return try {
            AppJson.instance.decodeFromString<T>(body)
        } catch (e: Exception) {
            throw LangGraphClientError.DecodingFailed(e.message ?: e.toString())
        }
    }

    private fun executeVoid(request: Request) {
        val (body, response) = readResponse(request)
        validate(response.code, body, request)
    }

    private fun readResponse(request: Request): Pair<String, Response> {
        val bodyBytes = snapshotRequestBody(request)
        var current = DeploymentURLNormalizer.prepareRequest(rebuildRequest(request, bodyBytes))
        var response = client.newCall(current).execute()
        response = followRedirects(current, response, bodyBytes)

        if (shouldRetryOpenResty405(response, current)) {
            response.close()
            current = DeploymentURLNormalizer.prepareRequest(rebuildRequest(current, bodyBytes))
            response = client.newCall(current).execute()
            response = followRedirects(current, response, bodyBytes)
        }

        val body = response.body?.string().orEmpty()
        return body to response
    }

    private fun snapshotRequestBody(request: Request): ByteArray? {
        val body = request.body ?: return null
        return Buffer().use { buffer ->
            body.writeTo(buffer)
            buffer.readByteArray()
        }
    }

    private fun rebuildRequest(request: Request, bodyBytes: ByteArray?): Request {
        if (bodyBytes == null) return request
        val mediaType = request.body?.contentType()
        return request.newBuilder()
            .method(request.method, bodyBytes.toRequestBody(mediaType))
            .build()
    }

    private fun followRedirects(
        request: Request,
        response: Response,
        bodyBytes: ByteArray?,
    ): Response {
        var currentRequest = request
        var currentResponse = response
        var redirectCount = 0

        while (currentResponse.code in 300..399 && redirectCount < 10) {
            val location = currentResponse.header("Location") ?: break
            currentResponse.close()

            val nextUrl = currentRequest.url.resolve(location) ?: break
            currentRequest = currentRequest.newBuilder()
                .url(nextUrl)
                .build()
            currentRequest = rebuildRequest(currentRequest, bodyBytes)
            currentRequest = DeploymentURLNormalizer.prepareRequest(currentRequest)

            currentResponse = client.newCall(currentRequest).execute()
            redirectCount++
        }

        return currentResponse
    }

    private fun shouldRetryOpenResty405(response: Response, request: Request): Boolean {
        if (response.code != 405 || request.method != "POST") return false
        val body = response.peekBody(1024).string()
        return body.contains("openresty", ignoreCase = true) ||
            body.contains("not allowed", ignoreCase = true)
    }

    private fun validate(statusCode: Int, body: String, request: Request) {
        if (statusCode !in 200..299) {
            throw LangGraphClientError.HttpError(
                statusCode = statusCode,
                body = body,
                requestUrl = request.url.toString(),
                method = request.method,
            )
        }
    }
}
