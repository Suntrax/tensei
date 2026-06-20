package com.blissless.tensei.network

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import kotlin.time.Duration.Companion.milliseconds

/**
 * Result wrapper for GraphQL requests with metadata
 */
data class GraphQLResult<T>(
    val data: T?,
    val error: GraphQLError?,
    val fromCache: Boolean,
    val retryCount: Int
)

data class GraphQLError(
    val message: String,
    val code: Int? = null,
    val isRateLimit: Boolean = false,
    val retryAfter: Long? = null
)

/**
 * Configuration for the GraphQL client
 */
data class GraphQLConfig(
    val maxConcurrentRequests: Int = 5,
    val minRequestIntervalMs: Long = 100L,
    val defaultTimeout: Int = 30000,
    val maxRetries: Int = 3,
    val baseRetryDelayMs: Long = 1000L,
    val maxRetryDelayMs: Long = 30000L,
    val cacheDurationMs: Long = 5 * 60 * 1000L, // 5 minutes for public data
    val userDataCacheDurationMs: Long = 60 * 60 * 1000L, // 1 hour for user/authenticated data
    val maxCacheSize: Int = 200
)

/**
 * High-performance GraphQL client with:
 * - Request queue with limited concurrency
 * - Automatic rate limit handling (HTTP 429)
 * - Request deduplication
 * - In-memory caching
 * - Exponential backoff retries
 * - Client ID rotation
 */
class GraphQLClient(
    private val endpoint: String = "https://graphql.anilist.co",
    private val config: GraphQLConfig = GraphQLConfig()
) {
    companion object;

    // Request queue channel (unlimited buffer)
    private val requestChannel = Channel<QueuedRequest<*>>(UNLIMITED)

    // Concurrency limiter
    private val concurrencySemaphore = Semaphore(config.maxConcurrentRequests)

    // Rate limit state
    private val rateLimitState = RateLimitState()

    // Response cache
    private val responseCache = ConcurrentHashMap<String, CacheEntry>()

    // Pending requests for deduplication
    private val pendingRequests = ConcurrentHashMap<String, Deferred<String?>>()

    // Client ID rotation
    private val clientIdCounter = AtomicInteger(0)

    // Minimum interval tracking
    @Volatile
    private var lastRequestTime = 0L
    private val intervalLock = Any()

    // Queue processing job
    private var queueProcessorJob: Job? = null

    // Scope for queue processing
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        startQueueProcessor()
    }

    fun getConfig() = config

    /**
     * Start the request queue processor
     */
    private fun startQueueProcessor() {
        queueProcessorJob = scope.launch {
            for (request in requestChannel) {
                processRequest(request)
            }
        }
    }

    /**
     * Process a single request from the queue
     */
    private suspend fun <T> processRequest(request: QueuedRequest<T>) {
        concurrencySemaphore.withPermit {
            // Check rate limit before processing
            waitForRateLimit()

            // Apply minimum interval between requests
            applyMinInterval()

            try {
                val result = executeRequestWithRetry(request)
                request.continuation.resume(result) { _, _, _ -> }
            } catch (e: Exception) {
                request.continuation.resume(
                    GraphQLResult(
                                    data = null,
                                    error = GraphQLError(e.message ?: "Unknown error"),
                                    fromCache = false,
                                    retryCount = 0
                                )
                ) { _, _, _ -> }
            }
        }
    }

    /**
     * Execute request with retry logic
     */
    private suspend fun <T> executeRequestWithRetry(request: QueuedRequest<T>): GraphQLResult<T> {
        var lastError: GraphQLError? = null
        var retryCount = 0

        repeat(config.maxRetries + 1) { attempt ->
            if (attempt > 0) {
                val delay = calculateBackoffDelay(attempt - 1, lastError?.retryAfter)
                kotlinx.coroutines.delay(delay.milliseconds)
            }

            val response = executeHttpRequest(request)

            when {
                response.isRateLimited -> {
                    lastError = response.error
                    handleRateLimit(response.retryAfter)
                    // Continue to retry
                }
                response.isSuccess -> {
                    val parsed = request.parser(response.body!!)
                    return GraphQLResult(
                        data = parsed,
                        error = null,
                        fromCache = false,
                        retryCount = attempt
                    )
                }
                else -> {
                    // Non-retriable error
                    return GraphQLResult(
                        data = null,
                        error = response.error,
                        fromCache = false,
                        retryCount = attempt
                    )
                }
            }
            retryCount = attempt + 1
        }

        return GraphQLResult(
            data = null,
            error = lastError ?: GraphQLError("Max retries exceeded"),
            fromCache = false,
            retryCount = retryCount
        )
    }

    /**
     * Execute the actual HTTP request
     */
    private suspend fun <T> executeHttpRequest(request: QueuedRequest<T>): HttpResponse {
        return withContext(Dispatchers.IO) {
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpsURLConnection

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")

                // Add auth if required
                if (request.requiresAuth && request.authToken != null) {
                    connection.setRequestProperty("Authorization", "Bearer ${request.authToken}")
                }

                // Rotate client ID
                val clientIdIndex = request.clientIdIndex.getAndIncrement() % request.clientIds.size
                connection.setRequestProperty("X-Client-Id", request.clientIds[clientIdIndex])

                connection.connectTimeout = config.defaultTimeout
                connection.readTimeout = config.defaultTimeout
                connection.doOutput = true

                // Build request body
                val body = buildRequestBody(request.query, request.variables)
                connection.outputStream.use { it.write(body.toByteArray()) }

                when (val responseCode = connection.responseCode) {
                    429 -> {
                        val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
                        HttpResponse(
                            isRateLimited = true,
                            retryAfter = retryAfter,
                            error = GraphQLError(
                                message = "Rate limited",
                                code = 429,
                                isRateLimit = true,
                                retryAfter = retryAfter
                            )
                        )
                    }
                    200 -> {
                        val responseBody = connection.inputStream.bufferedReader().readText()
                        HttpResponse(
                            isSuccess = true,
                            body = responseBody
                        )
                    }
                    else -> {
                        val errorBody = connection.errorStream?.bufferedReader()?.readText()
                        HttpResponse(
                            error = GraphQLError(
                                message = "HTTP $responseCode: ${errorBody?.take(200)}",
                                code = responseCode
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                HttpResponse(
                    error = GraphQLError(message = e.message ?: "Network error")
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Handle rate limit by updating state
     */
    private fun handleRateLimit(retryAfter: Long?) {
        synchronized(rateLimitState) {
            rateLimitState.isLimited = true
            rateLimitState.lastLimitedTime = System.currentTimeMillis()
            rateLimitState.retryAfterMs = (retryAfter ?: 60) * 1000 // Default to 60s if not specified
        }
    }

    /**
     * Wait if we're rate limited
     */
    private suspend fun waitForRateLimit() {
        val delayNeeded = synchronized(rateLimitState) {
            if (rateLimitState.isLimited) {
                val elapsed = System.currentTimeMillis() - rateLimitState.lastLimitedTime
                val remaining = rateLimitState.retryAfterMs - elapsed
                if (remaining > 0) {
                    remaining
                } else {
                    rateLimitState.isLimited = false
                    0L
                }
            } else {
                0L
            }
        }

        if (delayNeeded > 0) {
            kotlinx.coroutines.delay(delayNeeded.milliseconds)
            synchronized(rateLimitState) {
                rateLimitState.isLimited = false
            }
        }
    }

    /**
     * Apply minimum interval between requests
     */
    private suspend fun applyMinInterval() {
        val delayNeeded = synchronized(intervalLock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < config.minRequestIntervalMs) {
                config.minRequestIntervalMs - elapsed
            } else {
                0L
            }
        }

        if (delayNeeded > 0) {
            kotlinx.coroutines.delay(delayNeeded.milliseconds)
        }

        synchronized(intervalLock) {
            lastRequestTime = System.currentTimeMillis()
        }
    }

    /**
     * Calculate backoff delay with exponential increase
     */
    private fun calculateBackoffDelay(attempt: Int, retryAfter: Long?): Long {
        // Use Retry-After header if available
        if (retryAfter != null && retryAfter > 0) {
            return retryAfter * 1000
        }

        // Exponential backoff: baseDelay * 2^attempt with jitter
        val baseDelay = config.baseRetryDelayMs
        val exponentialDelay = baseDelay * (1 shl attempt)
        val jitter = (0..500).random().toLong()
        return minOf(exponentialDelay + jitter, config.maxRetryDelayMs)
    }

    /**
     * Build JSON request body
     */
    private fun buildRequestBody(query: String, variables: Map<String, Any?>): String {
        val variablesJson = if (variables.isEmpty()) "{}" else {
            variables.entries.joinToString(",", "{", "}") { (key, value) ->
                "\"$key\":${serializeValue(value)}"
            }
        }

        val escapedQuery = query
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return "{\"query\":\"$escapedQuery\",\"variables\":$variablesJson}"
    }

    private fun serializeValue(value: Any?): String = when (value) {
        is String -> "\"$value\""
        is Number -> value.toString()
        is Boolean -> value.toString()
        null -> "null"
        is List<*> -> value.joinToString(",", "[", "]") { serializeValue(it) }
        else -> "\"$value\""
    }

    /**
     * Generate cache key for request
     */
    private fun generateCacheKey(query: String, variables: Map<String, Any?>): String {
        val varsStr = variables.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        return "${query.hashCode()}_$varsStr"
    }

    /**
     * Get cached response if valid
     */
    private fun getCachedResponse(cacheKey: String, cacheDurationMs: Long = config.cacheDurationMs): String? {
        val entry = responseCache[cacheKey] ?: return null
        val now = System.currentTimeMillis()
        if (now - entry.timestamp < cacheDurationMs) {
            return entry.response
        }
        responseCache.remove(cacheKey)
        return null
    }

    /**
     * Cache response
     */
    private fun cacheResponse(cacheKey: String, response: String) {
        responseCache[cacheKey] = CacheEntry(
            response = response,
            timestamp = System.currentTimeMillis()
        )

        // Prune cache if too large
        if (responseCache.size > config.maxCacheSize) {
            val sortedKeys = responseCache.entries
                .sortedBy { it.value.timestamp }
                .take(responseCache.size - config.maxCacheSize / 2)
                .map { it.key }
            sortedKeys.forEach { responseCache.remove(it) }
        }
    }

    /**
     * Execute a GraphQL query with full optimization
     */
    suspend fun <T> execute(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        requiresAuth: Boolean = false,
        authToken: String? = null,
        clientIds: List<String>,
        useCache: Boolean = true,
        cacheDurationMs: Long? = null, // Override cache duration
        parser: (String) -> T?
    ): GraphQLResult<T> = suspendCancellableCoroutine { continuation ->
        val cacheKey = generateCacheKey(query, variables)
        val effectiveCacheDuration = cacheDurationMs ?: if (requiresAuth) config.userDataCacheDurationMs else config.cacheDurationMs

        // Check cache first if enabled
        if (useCache) {
            getCachedResponse(cacheKey, effectiveCacheDuration)?.let { cached ->
                val parsed = parser(cached)
                if (parsed != null) {
                    continuation.resume(
                        GraphQLResult(
                            data = parsed,
                            error = null,
                            fromCache = true,
                            retryCount = 0
                        )
                    ) { _, _, _ -> }
                    return@suspendCancellableCoroutine
                }
            }
        }

        // Check for pending duplicate request
        val existingPending = pendingRequests[cacheKey]
        if (existingPending != null) {
            scope.launch {
                try {
                    val response = existingPending.await()
                    if (response != null) {
                        val parsed = parser(response)
                        continuation.resume(
                            GraphQLResult(
                                data = parsed,
                                error = null,
                                fromCache = false,
                                retryCount = 0
                            )
                        ) { _, _, _ -> }
                    } else {
                        // Fall through to queue new request
                        queueRequest(query, variables, requiresAuth, authToken, clientIds, parser, cacheKey, continuation)
                    }
                } catch (_: Exception) {
                    queueRequest(query, variables, requiresAuth, authToken, clientIds, parser, cacheKey, continuation)
                }
            }
            return@suspendCancellableCoroutine
        }

        queueRequest(query, variables, requiresAuth, authToken, clientIds, parser, cacheKey, continuation)
    }

    private fun <T> queueRequest(
        query: String,
        variables: Map<String, Any?>,
        requiresAuth: Boolean,
        authToken: String?,
        clientIds: List<String>,
        parser: (String) -> T?,
        cacheKey: String,
        continuation: CancellableContinuation<GraphQLResult<T>>
    ) {
        val request = QueuedRequest(
            query = query,
            variables = variables,
            requiresAuth = requiresAuth,
            authToken = authToken,
            clientIds = clientIds,
            clientIdIndex = clientIdCounter,
            parser = { response ->
                // Cache successful response
                cacheResponse(cacheKey, response)
                // Complete deduplication deferred
                pendingRequests.remove(cacheKey) ?: Unit
                parser(response)
            },
            continuation = continuation
        )

        // Queue the request
        requestChannel.trySend(request).isSuccess.let { sent ->
            if (!sent) {
                continuation.resume(
                    GraphQLResult(
                                    data = null,
                                    error = GraphQLError("Request queue full"),
                                    fromCache = false,
                                    retryCount = 0
                                )
                ) { _, _, _ -> }
            }
        }
    }

    /**
     * Clear all caches
     */
    fun clearCache() {
        responseCache.clear()
        pendingRequests.clear()
    }

}

// ============================================
// Internal data classes
// ============================================

private data class RateLimitState(
    var isLimited: Boolean = false,
    var retryAfterMs: Long = 0,
    var lastLimitedTime: Long = 0
)

private data class QueuedRequest<T>(
    val query: String,
    val variables: Map<String, Any?>,
    val requiresAuth: Boolean,
    val authToken: String?,
    val clientIds: List<String>,
    val clientIdIndex: AtomicInteger,
    val parser: (String) -> T?,
    val continuation: CancellableContinuation<GraphQLResult<T>>
)

private data class CacheEntry(
    val response: String,
    val timestamp: Long
)

private data class HttpResponse(
    val isSuccess: Boolean = false,
    val isRateLimited: Boolean = false,
    val body: String? = null,
    val error: GraphQLError? = null,
    val retryAfter: Long? = null
)


