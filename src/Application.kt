package com.example

import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.cio.websocket.pingPeriod
import io.ktor.http.cio.websocket.timeout
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.sessions.*
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.generateNonce
import io.ktor.websocket.WebSockets
import java.time.Duration

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    println("Is testing: $testing")
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(Sessions) {
        cookie<ChatSession>("SESSION")
    }
    intercept(ApplicationCallPipeline.Features) {
        if (call.sessions.get<ChatSession>() == null) {
            call.sessions.set(ChatSession(generateNonce()))
        }
    }

    install(ContentNegotiation) {
        gson {

        }
    }

    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }

        /*webSocket("/chat") {
            // First of all we get the session.
            val session = call.sessions.get<ChatSession>()

            try {
                // We starts receiving messages (frames).
                // Since this is a coroutine. This coroutine is suspended until receiving frames.
                // Once the connection is closed, this consumeEach will finish and the code will continue.
                incoming.consumeEach { frame ->
                    // Frames can be [Text], [Binary], [Ping], [Pong], [Close].
                    // We are only interested in textual messages, so we filter it.
                    if (frame is Frame.Text) {
                        // Now it is time to process the text sent from the user.
                        // At this point we have context about this connection, the session, the text and the server.
                        // So we have everything we need.
                        receivedMessage(session!!.id, frame.readText())
                    }
                }
            } finally {
                // Either if there was an error, of it the connection was closed gracefully.
                // We notify the server that the member left.
                //server.memberLeft(session.id, this)
            }
        }*/

        pokerGame("/poker")

    }

}
