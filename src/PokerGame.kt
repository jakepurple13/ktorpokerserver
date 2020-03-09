package com.example

import com.example.cards.Deck
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.Routing
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.websocket.webSocket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach

val deck = Deck.defaultDeck().apply {
    trueRandomShuffle()
    addDeckListener {
        onDraw { _, i ->
            if (i <= 5) {
                this@apply.addDeck(Deck.defaultDeck())
                trueRandomShuffle()
            }
        }
        onShuffle { println("Shuffling") }
    }
}

val server = PokerServer()

@OptIn(ExperimentalCoroutinesApi::class)
fun Routing.pokerGame(path: String = "/poker") = webSocket(path) {
    val session = call.sessions.get<ChatSession>()

    if (session == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
        return@webSocket
    }

    server.memberJoin(session.id, this)

    try {
        incoming.consumeEach { frame -> if (frame is Frame.Text) receivedMessage(session.id, frame.readText()) }
    } finally {
        server.memberLeft(session.id, this)
    }
}

/**
 * A chat session is identified by a unique nonce ID. This nonce comes from a secure random source.
 */
data class ChatSession(val id: String)

suspend fun receivedMessage(id: String, command: String) {
    println("$id: $command")
    try {
        val cardPlay = command.fromJson<CardType>()
        when (cardPlay?.type) {
            Type.GET_HAND -> server.sendCards(id, cardPlay)
            Type.DRAW_CARDS -> server.drawCards(id, cardPlay)
            Type.SUBMIT_HAND -> server.submitHand(id, cardPlay)
            Type.CHAT -> server.message(id, command)
            Type.RENAME -> server.memberRenamed(id, cardPlay<String>()!!)
            else -> Unit
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

enum class Type {
    DRAW_CARDS, GET_HAND, UPDATE, CHAT, SUBMIT_HAND, RENAME
}

data class CardType(val type: Type, val any: Any) {
    inline fun <reified T> getAnyType() = any.toJson().fromJson<T>()
    fun toFrameJson() = Frame.Text(toJson())
    inline operator fun <reified T> invoke() = getAnyType<T>()
}