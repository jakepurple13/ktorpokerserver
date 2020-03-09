package com.example

import com.example.cards.Card
import com.example.cards.PokerHand
import com.example.cards.Scores
import com.example.cards.Suit
import io.github.serpro69.kfaker.Faker
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testRoot() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/").apply {
                println(this.response.content)
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("HELLO WORLD!", response.content)
            }
        }
    }

    val hostUrl = "http://0.0.0.0:8080"

    suspend fun drawCards(amount: Int) = HttpClient().get<String>("$hostUrl/drawCards$amount.json").fromJson<List<Card>>()!!
    suspend fun draw() = HttpClient().get<String>("$hostUrl/draw.json").fromJson<List<Card>>()!!
    suspend fun getHand(cards: List<Card>) = HttpClient().put<String>("$hostUrl/playCards") { this.body = cards.toJson() }.fromJson<PokerHand>()

    @Test
    fun cardTest() {
        val hand = mutableListOf<Card>()
        runBlocking {
            hand += draw()
            hand.removeAt(0)
            hand.removeAt(0)
            hand.removeAt(0)
            hand.removeAt(0)
            hand += drawCards(4)
            val pokerHand = getHand(hand)
            println(pokerHand)
            println("$hand")
        }
    }

    data class CardPlay(val type: String, val cards: List<Card>)

    @Test
    fun chatTest() {
        val f =
            "{\"type\":\"submitHand\",\"cards\":\"[{\"value\":8,\"suit\":\"CLUBS\"},{\"value\":6,\"suit\":\"CLUBS\"},{\"value\":12,\"suit\":\"HEARTS\"},{\"value\":2,\"suit\":\"SPADES\"},{\"value\":4,\"suit\":\"HEARTS\"}]\"}"
        println(f)
        println(f.fromJson<CardPlay>())

        val f5 = "{\"type\":\"submitHand\",\"cards\":[{\"value\":10,\"suit\":\"DIAMONDS\"}]}"
        println(f5)
        println(f5.fromJson<CardPlay>())

        val f1 = Card.RandomCard
        println(f1.toJson())
        println(f1.toJson().fromJson<Card>())

        val f6 =
            "{\"type\":\"SUBMIT_HAND\",\"any\":[{\"value\":10,\"suit\":\"DIAMONDS\"},{\"value\":11,\"suit\":\"DIAMONDS\"},{\"value\":2,\"suit\":\"DIAMONDS\"},{\"value\":8,\"suit\":\"HEARTS\"},{\"value\":7,\"suit\":\"HEARTS\"}]}"
        println(f6)
        println(f6.fromJson<CardPlay>() ?: throw Exception(""))
        val c = f6.fromJson<CardType>()!!
        println(c)
        println(c.any)
        println(c<List<Card>>()!!.joinToString { "${it.symbol}${it.suit.unicodeSymbol}" })
    }

    @Test
    fun other() {
        val f = 3
        println(f)
        println(f.toJson())
        println(f.toJson().fromJson<Int>())

        val f1 = 0..10
        println(f1.toJson())
        println(f1.toJson().fromJson<IntRange>())
    }

    @Test
    fun handCheck() {

        //highest.key to listOf(highest.value.maxBy { it.hand.sumBy { if (it.value == 1) 14 else it.value } }!!)

        val scores = Scores()
        val hands = listOf(
            listOf(Card(2, Suit.SPADES), Card(1, Suit.HEARTS), Card(4, Suit.SPADES), Card(5, Suit.HEARTS), Card(6, Suit.SPADES)),
            listOf(Card(3, Suit.SPADES), Card(1, Suit.HEARTS), Card(4, Suit.SPADES), Card(5, Suit.HEARTS), Card(6, Suit.SPADES))
        )

        val otherHands = hands.groupBy { scores.getWinningHand(it) }
        val highest = otherHands.maxBy { it.key.defaultWinning }!!
        println(highest)
        val high = if (highest.value.size > 1) {
            highest.key to listOf(highest.value.maxBy { it.map { it.value }.maxBy { if (it == 1) 14 else it }!! })
        } else highest.toPair()

        println(high)
        println(compareByHighCard(highest.value))
        println("---")
        println(findBestHand(hands))
        println("---")
        println(findBestHand2(hands))
        val hands2 = listOf(
            ChatUser("Jake", hand = listOf(Card(2, Suit.SPADES), Card(1, Suit.HEARTS), Card(4, Suit.SPADES), Card(5, Suit.HEARTS), Card(6, Suit.SPADES))),
            ChatUser("Jacob", hand = listOf(Card(3, Suit.SPADES), Card(1, Suit.HEARTS), Card(4, Suit.SPADES), Card(5, Suit.HEARTS), Card(6, Suit.SPADES)))
        )
        println("---")
        println(findBestHand(hands2))
    }

    /*private fun compareByHighCard(cards: List<List<Card>>, index: Int = 4) = when {
        (index < 0) -> false
        cards[index].weight === other.cards[index].weight -> compareByHighCard(other, index - 1)
        cards[index].weight.ordinal > other.cards[index].weight.ordinal -> true
        else -> false
    }*/

    data class ChatUser(
        var name: String,
        var image: String = "https://www.w3schools.com/w3images/bandmember.jpg",
        var hand: List<Card> = emptyList(),
        var submitted: Boolean = false
    )

    private val scores = Scores()

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
                if (checkValue < hand[i].valueAce) {
                    highestCard[index] = hand[i]
                } else if (checkValue == hand[i].valueAce) {
                    index++
                }
            }
        }
        val highCard = highestCard.sortedByDescending { it?.valueAce }.lastOrNull { it != null }
        return hands.key to cards.filter { it.hand.any { it.value == highCard?.value } }
    }

    private fun findBestHand(cards: List<List<Card>>) {
        val hands = cards.groupBy { scores.getWinningHand(it) }.maxBy { it.key.defaultWinning }!!
        val highestCard = arrayOfNulls<Card>(5)
        var index = 0
        hands.value.forEach {
            it.sortedByDescending { it.valueAce }.forEach {
                val checkValue = highestCard[index]?.valueAce ?: 0
                println("$checkValue and ${it.valueAce}")
                if (checkValue < it.valueAce) {
                    highestCard[index] = it
                } else if (checkValue == it.valueAce) {
                    index++
                }
            }
        }
        println(highestCard.joinToString { "$it" })

        //val high = hands.value.maxBy { it.maxBy { it.valueAce }!!.valueAce }
        //println(high)
    }

    private fun findBestHand2(cards: List<List<Card>>) {
        val hands = cards.groupBy { scores.getWinningHand(it) }.maxBy { it.key.defaultWinning }!!
        val highestCard = arrayOfNulls<Card>(5)
        var index = 0
        for(i in 0 until 5) {
            hands.value.forEach {
                val list = it.sortedByDescending { it.valueAce }
                val checkValue = highestCard[index]?.valueAce ?: 0
                if (checkValue < list[i].valueAce) {
                    highestCard[index] = list[i]
                } else if (checkValue == list[i].valueAce) {
                    index++
                }
            }
        }
        println(highestCard.joinToString { "$it" })
        println(highestCard.sortedByDescending { it?.valueAce })
        val highCard = highestCard.sortedByDescending { it?.valueAce }.lastOrNull { it != null }
        println(cards.find { highCard in it })
    }

    private fun compareByHighCard(cards: List<List<Card>>, index: Int = 4): Pair<Int, List<Card>>? {
        val mapped = cards.map { it[4] }.groupBy { it.value }.maxBy { if (it.key == 1) 14 else it.key }!!
        return when {
            (index < 0) -> null
            mapped.value.size > 1 -> compareByHighCard(cards, index - 1)
            mapped.value.size == 1 -> mapped.toPair()
            else -> null
        }
    }

    @Before
    fun setup() {
        println("-".repeat(50))
    }

    @After
    fun after() {
        println("-".repeat(50))
    }

    @Test
    fun faker() {
        val f = Faker()
        f.unique.enable(f::funnyName)
        //for(i in 0..20) println(f.hitchhikersGuideToTheGalaxy.characters())
        for(i in 0..100) println(f.funnyName.name())
    }

}
