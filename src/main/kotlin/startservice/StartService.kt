/**
 * Starts a Skir service on http://localhost:8787/myapi
 *
 * Run with: ./gradlew run -PmainClass=examples.startservice.StartServiceKt
 *
 * Use 'CallService.kt' to call this service from another process.
 */

package examples.startservice

import build.skir.UnrecognizedValuesPolicy
import build.skir.service.Service
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import skirout.service.AddUser
import skirout.service.AddUserRequest
import skirout.service.AddUserResponse
import skirout.service.GetUser
import skirout.service.GetUserRequest
import skirout.service.GetUserResponse
import skirout.user.User
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Custom request metadata that includes both request and response headers. */
data class RequestMetadata(
    val requestHeaders: Map<String, String>,
    val responseHeaders: MutableMap<String, String>,
)

/** Implementation of the service methods. */
class ServiceImpl {
    private val idToUser = mutableMapOf<Int, User>()

    fun getUser(request: GetUserRequest): GetUserResponse {
        val userId = request.userId
        val user = idToUser[userId]
        return GetUserResponse.partial(user = user)
    }

    fun addUser(
        request: AddUserRequest,
        metadata: RequestMetadata,
    ): AddUserResponse {
        val user = request.user
        if (user.userId == 0) {
            throw IllegalArgumentException("invalid user id")
        }
        println("Adding user: $user")
        idToUser[user.userId] = user

        // Example of using request/response headers
        val fooHeader = metadata.requestHeaders["x-foo"] ?: ""
        metadata.responseHeaders["x-bar"] = fooHeader.uppercase()

        return AddUserResponse.partial()
    }
}

fun main() {
    val serviceImpl = ServiceImpl()

    // Build the Skir service with custom metadata
    val skirService =
        Service.builder<RequestMetadata> { httpHeaders ->
            val requestHeaders = mutableMapOf<String, String>()
            httpHeaders.map().forEach { (key, values) ->
                if (values.isNotEmpty()) {
                    requestHeaders[key.lowercase()] = values[0]
                }
            }
            val responseHeaders = mutableMapOf<String, String>()
            RequestMetadata(requestHeaders, responseHeaders)
        }
            .addMethod(AddUser) { req, meta -> serviceImpl.addUser(req, meta) }
            .addMethod(GetUser) { req, meta -> serviceImpl.getUser(req) }
            .build()

    // Create HTTP server
    val server = HttpServer.create(InetSocketAddress("localhost", 8787), 0)

    // Root handler
    server.createContext("/") { exchange ->
        val response = "Hello, World!"
        exchange.sendResponseHeaders(200, response.length.toLong())
        exchange.responseBody.use { os ->
            os.write(response.toByteArray(StandardCharsets.UTF_8))
        }
    }

    // API handler
    server.createContext("/myapi") { exchange ->
        try {
            println("Request: ${exchange.requestMethod} ${exchange.requestURI}")

            // Read request body
            val requestBody =
                if (exchange.requestMethod == "POST") {
                    exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
                } else {
                    // For GET requests, use the query string
                    val query = exchange.requestURI.query
                    if (query != null) URLDecoder.decode(query, StandardCharsets.UTF_8) else ""
                }

            // Convert headers to the format expected by Service
            val httpHeaders = java.net.http.HttpHeaders.of(exchange.requestHeaders) { _, _ -> true }

            // Handle the request
            val rawResponse =
                runBlocking {
                    skirService.handleRequest(
                        requestBody,
                        httpHeaders,
                        UnrecognizedValuesPolicy.KEEP,
                    )
                }

            // Send response
            exchange.responseHeaders["Content-Type"] = rawResponse.contentType

            println("Raw response data: ${rawResponse.data}")
            val responseBytes = rawResponse.data.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(rawResponse.statusCode, responseBytes.size.toLong())
            exchange.responseBody.use { os ->
                os.write(responseBytes)
            }
        } catch (e: IOException) {
            throw e
        } catch (e: InterruptedException) {
            System.err.println("Request interrupted: ${e.message}")
            Thread.currentThread().interrupt()
            val errorResponse = "Request interrupted"
            val errorBytes = errorResponse.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(500, errorBytes.size.toLong())
            exchange.responseBody.use { os ->
                os.write(errorBytes)
            }
        } catch (e: RuntimeException) {
            System.err.println("Error handling request: ${e.message}")
            val errorResponse = "Server error: ${e.message}"
            val errorBytes = errorResponse.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(500, errorBytes.size.toLong())
            exchange.responseBody.use { os ->
                os.write(errorBytes)
            }
        }
    }

    server.executor = null // creates a default executor
    server.start()
    println("Serving at http://localhost:8787")
    println("API endpoint: http://localhost:8787/myapi")
    println("Press Ctrl+C to stop the server")
}
