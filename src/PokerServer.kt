package com.example

import com.example.cards.Card
import com.example.cards.PokerHand
import com.example.cards.Scores
import com.google.gson.Gson
import io.github.serpro69.kfaker.Faker
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession
import io.ktor.http.cio.websocket.close
import kotlinx.coroutines.channels.ClosedSendChannelException
import java.awt.Color
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

data class ChatUser(
    var name: String,
    var hand: List<Card> = emptyList(),
    var submitted: Boolean = false,
    var money: UserMoney = UserMoney()
)

data class UserMoney(var money: Double = 20.0, var anted: Boolean = false)

class PokerServer {
    /**
     * Atomic counter used to get unique user-names based on the maximum users the server had.
     */
    private val usersCounter = AtomicInteger()

    /**
     * A concurrent map associating session IDs to user names.
     */
    private val memberNames = ConcurrentHashMap<String, ChatUser>()

    /**
     * Associates a session-id to a set of websockets.
     * Since a browser is able to open several tabs and windows with the same cookies and thus the same session.
     * There might be several opened sockets for the same client.
     */
    private val members = ConcurrentHashMap<String, MutableList<WebSocketSession>>()

    /**
     * A list of the latest messages sent to the server, so new members can have a bit context of what
     * other people was talking about before joining.
     */
    private val lastMessages = LinkedList<SendMessage>()

    private val scores = Scores()

    private val faker = Faker().apply { unique.enable(this::funnyName) }

    /**
     * Handles that a member identified with a session id and a socket joined.
     */
    suspend fun memberJoin(member: String, socket: WebSocketSession) {
        // Checks if this user is already registered in the server and gives him/her a temporal name if required.
        //val name = memberNames.computeIfAbsent(member) { ChatUser("user${usersCounter.incrementAndGet()}") }
        val name = memberNames.computeIfAbsent(member) {
            //usersCounter.incrementAndGet()
            //val n = faker.funnyName.name()//randomName()
            /*while (memberNames.values.any { it.name == n }) {
                n = ""//randomName()
            }*/
            ChatUser(faker.funnyName.name())
        }

        println("Member joined: $name")

        // Associates this socket to the member id.
        // Since iteration is likely to happen more frequently than adding new items,
        // we use a `CopyOnWriteArrayList`.
        // We could also control how many sockets we would allow per client here before appending it.
        // But since this is a sample we are not doing it.
        val list = members.computeIfAbsent(member) { CopyOnWriteArrayList<WebSocketSession>() }
        list.add(socket)

        socket.send(CardType(Type.UPDATE, name.name).toFrameJson())
        socket.send(CardType(Type.UPDATE, memberNames.map { it.value.name }).toFrameJson())

        // Only when joining the first socket for a member notifies the rest of the users.
        if (list.size == 1) {
            //broadcastUserUpdate()
            //val sendMessage = SendMessage(ChatUser("Server"), "Connected as ${name.name}", MessageType.SERVER)
            //members[member]?.send(CardType(Type.UPDATE, sendMessage.toJson()).toFrameJson())
        }

        // Sends the user the latest messages from this server to let the member have a bit context.
        /*val messages = synchronized(lastMessages) { lastMessages.toList() }
        for (message in messages) {
            //socket.send(CardType(Type.UPDATE, message.toJson()).toFrameJson())
        }*/
    }

    private var pot = 0.0
    private var ante = 5

    suspend fun ante(sender: String) {
        val member = memberNames[sender]
        member?.money?.money = member?.money?.money?.minus(ante)!!
        pot += ante
        member.money.anted = true
        members[sender]?.send(CardType(Type.ANTE, "You anted \$$ante. You have \$${member.money.money}").toFrameJson())
        if (checkAll { it.value.money.anted } && memberNames.size > 1) broadcast("Everyone has anted. The pot is $pot.")
    }

    suspend fun betMoney(sender: String, cardPlay: CardType) {
        val play = cardPlay<Double>() ?: 0.0
        val member = memberNames[sender]?.money
        if (member?.money ?: 0 - play >= 0) {
            member?.money = member?.money?.minus(play)!!
            pot += play
        }
        members[sender]?.send(CardType(Type.BET_MONEY, "You bet \$$play. The pot is now $pot.").toFrameJson())
    }

    private fun checkAll(predicate: (Map.Entry<String, ChatUser>) -> Boolean) = memberNames.all(predicate) && memberNames.isNotEmpty()

    suspend fun moneyCheck(sender: String) {
        members[sender]?.send(CardType(Type.MONEY_CHECK, memberNames[sender]?.money?.money!!).toFrameJson())
    }

    suspend fun sendCards(sender: String, cardPlay: CardType) {
        members[sender]?.send(CardType(Type.GET_HAND, scores.getWinningHand(cardPlay.any.toJson().fromJson<List<Card>>()!!)).toFrameJson())
    }

    suspend fun drawCards(sender: String, cardPlay: CardType) {
        members[sender]?.send(CardType(Type.DRAW_CARDS, deck.draw(cardPlay.any.toJson().fromJson<Int>()!!)).toFrameJson())
    }

    suspend fun submitHand(sender: String, cardPlay: CardType) {
        memberNames[sender]?.hand = cardPlay.getAnyType<List<Card>>()!!
        memberNames[sender]?.submitted = true
        submittedHandCheck()
    }

    private suspend fun submittedHandCheck() {
        if (checkAll { it.value.submitted }) {
            //val otherHands = memberNames.elements().toList().groupBy { scores.getWinningHand(it.hand) }
            //val highest = otherHands.maxBy { it.key.defaultWinning }!!
            /*val high = if (highest.value.size > 1)
                highest.key to listOf(highest.value.maxBy { it.hand.map(Card::value).maxBy { if (it == 1) 14 else it }!! }!!)
            else highest.toPair()*/
            val high = findBestHand(memberNames.elements().toList())
            val allHands = memberNames.elements().toList().sortedByDescending { scores.getWinningHand(it.hand).defaultWinning }.joinToString("\n") {
                "${it.name} had a ${scores.getWinningHand(it.hand).stringName} with: ${it.hand.map { "${it.symbol}${it.suit.unicodeSymbol}" }}"
            }
            broadcast("${high.second.joinToString(", ") { it.name }} won \$$pot with a ${high.first.stringName}\n$allHands")
            high.second.forEach { it.money.money += pot / high.second.size }
            memberNames.forEach {
                it.value.submitted = false
                it.value.hand = emptyList()
                it.value.money.anted = false
            }
            pot = 0.0
            /*memberNames.forEach {
                if(it.value.money.money<=0) {

                }
            }*/
        }
    }

    private val Card.valueAce get() = if (value == 1) 14 else value
    private fun findBestHand(cards: List<ChatUser>): Pair<PokerHand, List<ChatUser>> {
        val hands = cards.groupBy { scores.getWinningHand(it.hand) }.maxBy { it.key.defaultWinning }!!
        if (hands.value.size == 1) return hands.toPair()
        val highestCard = arrayOfNulls<Card>(5)
        var index = 0
        for (i in 0 until 5) {
            hands.value.forEach {
                val hand = it.hand.sortedByDescending { it.valueAce }
                val checkValue = highestCard[index]?.valueAce ?: 0
                if (checkValue < hand[i].valueAce) highestCard[index] = hand[i]
                else if (checkValue == hand[i].valueAce) index++
            }
        }
        val highCard = highestCard.sortedByDescending { it?.valueAce }.lastOrNull { it != null }
        return hands.key to cards.filter { it.hand.any { it.value == highCard?.value } }
    }

    /**
     * Handles a [member] idenitified by its session id renaming [to] a specific name.
     */
    suspend fun memberRenamed(member: String, to: String) {
        // Re-sets the member name.
        println("Member renamed: From: ${memberNames[member]?.name} To: $to")
        memberNames[member]?.name = to
        // Notifies everyone in the server about this change.
        //broadcastUserUpdate()
    }

    /**
     * Handles that a [member] with a specific [socket] left the server.
     */
    suspend fun memberLeft(member: String, socket: WebSocketSession) {
        // Removes the socket connection for this member
        val connections = members[member]
        connections?.remove(socket)

        // If no more sockets are connected for this member, let's remove it from the server
        // and notify the rest of the users about this event.
        if (connections != null && connections.isEmpty()) {
            val name = memberNames.remove(member)
            println("Member left: $name")
            //broadcast("server", "Member left: $name.", MessageType.SERVER)
            //broadcastUserUpdate()
        }
    }

    /**
     * Handles the 'who' command by sending the member a list of all all members names in the server.
     */
    suspend fun who(sender: String) {
        val text = memberNames.values.joinToString(prefix = "[server::who] ") { it.name }
        val sendMessage = SendMessage(ChatUser("Server"), text, MessageType.SERVER)
        members[sender]?.send(Frame.Text(sendMessage.toJson()))
    }

    private fun getMemberByUsername(userName: String) = memberNames.search(1L) { id, user ->
        if (user.name == userName) id else null
    }

    /**
     * Handles sending to a [recipient] from a [sender] a [message].
     *
     * Both [recipient] and [sender] are identified by its session-id.
     */
    suspend fun sendTo(recipient: String, sender: String, message: String) {
        val sendToUser = getMemberByUsername(recipient)
        if (sendToUser == null) {
            val sendMessage = SendMessage(ChatUser("Server"), "User not found", MessageType.SERVER)
            members[sender]?.send(Frame.Text(sendMessage.toJson()))
        } else {
            val user = memberNames[sender]!!
            val sendMessage = SendMessage(user, "(${user.name} => $recipient) $message", MessageType.MESSAGE, data = "pm")
            members[sendToUser]?.send(Frame.Text(sendMessage.toJson()))
            members[sender]?.send(Frame.Text(sendMessage.toJson()))
        }
        //prettyLog("$recipient\n$sendToUser\n$message")
    }

    /**
     * Handles a [message] sent from a [sender] by notifying the rest of the users.
     */
    suspend fun message(sender: String, message: String) {
        // Pre-format the message to be send, to prevent doing it for all the users or connected sockets.
        val name = memberNames[sender]?.name ?: sender
        val formatted = "$name: $message"
        // Sends this pre-formatted message to all the members in the server.
        broadcast(sender, formatted, MessageType.MESSAGE)
    }

    /**
     * Handles a [message] sent from a [sender] by notifying the rest of the users.
     */
    suspend fun actionMessage(sender: String, message: String) {
        // Pre-format the message to be send, to prevent doing it for all the users or connected sockets.
        val name = memberNames[sender]?.name ?: sender
        val formatted = "[i]$name$message[/i]"

        // Sends this pre-formatted message to all the members in the server.
        broadcast(sender, formatted, MessageType.MESSAGE)
    }

    private suspend fun somethingWentWrong(sender: String, message: String = "Something went wrong") {
        val sendMessage =
            SendMessage(ChatUser("Server"), message, MessageType.SERVER)
        members[sender]?.send(Frame.Text(sendMessage.toJson()))
    }

    enum class MessageType {
        MESSAGE, EPISODE, SERVER, INFO, TYPING_INDICATOR, DOWNLOADING
    }

    data class SendMessage(val user: ChatUser, val message: String, val type: MessageType?, val data: Any? = null) {
        val time = SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())!!
        fun toJson(): String = Gson().toJson(this)
    }

    suspend fun sendServerMessage(msg: String) {
        broadcast(SendMessage(ChatUser("Server"), msg, MessageType.SERVER).toJson())
    }

    /**
     * Sends a [message] to all the members in the server, including all the connections per member.
     */
    private suspend fun broadcast(message: String) {
        members.values.forEach { socket ->
            socket.send(CardType(Type.CHAT, message).toFrameJson())
        }
    }

    private suspend fun broadcastUserUpdate() {
        /*members.values.forEach { sockets ->
            sockets.send(Frame.Text(SendMessage(
                ChatUser("Server"),
                "${SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())} ",
                MessageType.INFO,
                memberNames.values.joinToString("\n") { it.name }
            ).toJson()))
        }*/
        val message = SendMessage(
            ChatUser("Server"),
            "",
            MessageType.INFO,
            memberNames.values
        ).toJson()
        members.values.forEach { sockets ->
            sockets.send(CardType(Type.UPDATE, message).toFrameJson())
        }
    }

    /**
     * Sends a [message] to all the members in the server, including all the connections per member.
     */
    private suspend fun broadcast(message: String, recipient: String) {
        members[recipient]?.send(Frame.Text(message))
    }

    /**
     * Sends a [message] coming from a [sender] to all the members in the server, including all the connections per member.
     */
    private suspend fun broadcast(
        sender: String,
        message: String,
        type: MessageType = MessageType.MESSAGE,
        data: Any? = null
    ) {
        //val name = memberNames[sender]?.name ?: sender
        //val text = "${SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())} $message"
        val sendMessage = SendMessage(memberNames[sender] ?: ChatUser("Server"), message, type, data)
        broadcast(CardType(Type.CHAT, sendMessage).toJson())
        //prettyLog(sendMessage.toJson())
        if (type != MessageType.TYPING_INDICATOR) {
            synchronized(lastMessages) {
                lastMessages.add(sendMessage)
                if (lastMessages.size > 100) {
                    lastMessages.removeFirst()
                }
            }
        }
    }

    /**
     * Sends a [message] to a list of [this] [WebSocketSession].
     */
    private suspend fun List<WebSocketSession>.send(frame: Frame) {
        forEach {
            try {
                it.send(frame.copy())
            } catch (t: Throwable) {
                try {
                    it.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, ""))
                } catch (ignore: ClosedSendChannelException) {
                    // at some point it will get closed
                }
            }
        }
    }
}