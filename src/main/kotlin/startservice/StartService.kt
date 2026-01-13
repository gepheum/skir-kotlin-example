/**
 * Starts a Skir service on http://localhost:8787/myapi
 *
 * Run with: ./gradlew run -PmainClass=examples.startservice.StartServiceKt
 *
 * Use 'CallService.kt' to call this service from another process.
 */

package examples.startservice

import build.skir.service.Service
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import skirout.service.AddUser
import skirout.service.AddUserRequest
import skirout.service.AddUserResponse
import skirout.service.GetUser
import skirout.service.GetUserRequest
import skirout.service.GetUserResponse
import skirout.user.User
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Custom data class containing relevant information extracted from the HTTP
 * request headers.
 */
data class RequestMetadata(
    val isAdmin: Boolean,
    // Add fields here.
) {
    companion object {
        fun fromHeaders(headers: Headers): RequestMetadata {
            return RequestMetadata(false)
        }
    }
}

/** Implementation of the service methods. */
class ServiceImpl {
    private val idToUser = mutableMapOf<Int, User>()

    fun getUser(request: GetUserRequest): GetUserResponse {
        val userId = request.userId
        val user = idToUser[userId]
        return GetUserResponse.partial(user = user)
    }

    suspend fun addUser(
        request: AddUserRequest,
        metadata: RequestMetadata,
    ): AddUserResponse {
        delay(1L)
        val user = request.user
        if (user.userId == 0) {
            throw IllegalArgumentException("invalid user id")
        }
        println("Adding user: $user")
        idToUser[user.userId] = user

        return AddUserResponse.partial()
    }
}

fun main() {
    val serviceImpl = ServiceImpl()

    // Build the Skir service with custom metadata
    val skirService =
        Service.Builder<RequestMetadata>()
            .addMethod(AddUser) { req, meta -> serviceImpl.addUser(req, meta) }
            .addMethod(GetUser) { req, _ -> serviceImpl.getUser(req) }
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
        // Read request body
        val requestBody =
            if (exchange.requestMethod == "POST") {
                exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            } else {
                // For GET requests, use the query string
                val query = exchange.requestURI.query
                if (query != null) URLDecoder.decode(query, StandardCharsets.UTF_8) else ""
            }

        // Handle the request
        val rawResponse =
            runBlocking {
                skirService.handleRequest(
                    requestBody,
                    RequestMetadata.fromHeaders(exchange.requestHeaders),
                )
            }

        // Send response
        exchange.responseHeaders["Content-Type"] = rawResponse.contentType

        val responseBytes = rawResponse.data.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(rawResponse.statusCode, responseBytes.size.toLong())
        exchange.responseBody.use { os ->
            os.write(responseBytes)
        }
    }

    server.executor = null // creates a default executor
    server.start()
    println("Serving at http://localhost:8787")
    println("API endpoint: http://localhost:8787/myapi")
    println("Press Ctrl+C to stop the server")
}
