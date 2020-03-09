package com.example.cards

import com.example.AnsiColor


class Scores {

    val values: String
        get() {
            val max = PokerHand.values().maxBy { it.stringName.length }!!.stringName.length
            val hands = PokerHand
                .values().sortedBy(PokerHand::defaultWinning).mapIndexedNotNull { index, hand ->
                    if (hand == PokerHand.HIGH_CARD) return@mapIndexedNotNull null
                    var s = ""
                    s += "${hand.stringName}:${" ".repeat(max - hand.stringName.length)}|"
                    for (j in 1..5) {
                        val next = (index.toDouble() * j.toDouble() * (hand.defaultWinning.toDouble() / index)).toInt()
                        s += "${"\u2007".repeat(4 - next.toString().length)}$next|"
                    }
                    s
                }.reversed()
            return hands.joinToString("\n")
        }

    fun htmlValues(typeHand: PokerHand, betAmount: Int): String {
        val max = PokerHand.values().maxBy { it.stringName.length }!!.stringName.length
        val hands = PokerHand
            .values().sortedBy(PokerHand::defaultWinning).mapIndexedNotNull { index, hand ->
                val correctHand = if (hand == PokerHand.HIGH_CARD) return@mapIndexedNotNull null else hand == typeHand
                var s = ""
                if (index != 0 && correctHand) s += "<font color=\"red\">"
                s += "${hand.stringName}:${" ".repeat(max - hand.stringName.length)}|"
                for (j in 1..5) {
                    val next = (index.toDouble() * j.toDouble() * (hand.defaultWinning.toDouble() / index)).toInt()
                    val sequenceSpaceNext = "${"\u2007".repeat(4 - next.toString().length)}$next"
                    s += "${(if (j == betAmount) "</font><font color =\"green\">$sequenceSpaceNext</font>${if (correctHand) "<font color=\"red\">" else ""}|"
                    else "$sequenceSpaceNext|")}${if (correctHand && j == 5) "</font>" else ""}"
                }
                s
            }.reversed()
        return hands.joinToString("", prefix = "<html>", postfix = "</html>") { "<p>$it</p>" }
    }

    fun htmlValues(betAmount: Int): String {
        val max = PokerHand.values().maxBy { it.stringName.length }!!.stringName.length
        val hands = PokerHand
            .values().sortedBy(PokerHand::defaultWinning).mapIndexedNotNull { index, hand ->
                if (hand == PokerHand.HIGH_CARD) return@mapIndexedNotNull null
                var s = ""
                s += "${hand.stringName}:${" ".repeat(max - hand.stringName.length)}|"
                for (j in 1..5) {
                    val next = (index.toDouble() * j.toDouble() * (hand.defaultWinning.toDouble() / index)).toInt()
                    val sequenceSpaceNext = "${"\u2007".repeat(4 - next.toString().length)}$next"
                    s += "${(if (j == betAmount) "<font color =\"green\">$sequenceSpaceNext</font>|"
                    else "$sequenceSpaceNext|")}${if (j == 5) "</font>" else ""}"
                }
                s
            }.reversed()
        return hands.joinToString("", prefix = "<html>", postfix = "</html>") { "<p>$it</p>" }
    }

    fun ansiValues(typeHand: PokerHand, betAmount: Int): String {
        val max = PokerHand.values().maxBy { it.stringName.length }!!.stringName.length
        val hands = PokerHand
            .values().sortedBy(PokerHand::defaultWinning).mapIndexedNotNull { index, hand ->
                val correctHand = if (hand == PokerHand.HIGH_CARD) return@mapIndexedNotNull null else hand == typeHand
                var s = ""
                if (index != 0 && correctHand) s += AnsiColor.regularColor(255, 0, 0)
                s += "${hand.stringName}:${" ".repeat(max - hand.stringName.length)}|"
                for (j in 1..5) {
                    val next = (index.toDouble() * j.toDouble() * (hand.defaultWinning.toDouble() / index)).toInt()
                    val sequenceSpaceNext = "${"\u2007".repeat(4 - next.toString().length)}$next"
                    s += "${(if (j == betAmount) "${AnsiColor.RESET}${
                    AnsiColor.colorText(sequenceSpaceNext, 0, 255, 0)
                    }${if (correctHand) AnsiColor.regularColor(255, 0, 0) else ""}|"
                    else "$sequenceSpaceNext|")}${if (correctHand && j == 5) AnsiColor.RESET else ""}"
                }
                s
            }.reversed()
        return hands.joinToString("\n")
    }

    fun ansiValues(betAmount: Int): String {
        val max = PokerHand.values().maxBy { it.stringName.length }!!.stringName.length
        val hands = PokerHand
            .values().sortedBy(PokerHand::defaultWinning).mapIndexedNotNull { index, hand ->
                if (hand == PokerHand.HIGH_CARD) return@mapIndexedNotNull null
                var s = ""
                s += "${hand.stringName}:${" ".repeat(max - hand.stringName.length)}|"
                for (j in 1..5) {
                    val next = (index.toDouble() * j.toDouble() * (hand.defaultWinning.toDouble() / index)).toInt()
                    val sequenceSpaceNext = "${"\u2007".repeat(4 - next.toString().length)}$next"
                    s += "${(if (j == betAmount) "${AnsiColor.colorText(sequenceSpaceNext, 0, 255, 0)}|"
                    else "$sequenceSpaceNext|")}${if (j == 5) "" else ""}"
                }
                s
            }.reversed()
        return hands.joinToString("\n")
    }

    var jacksOrBetter = false

    private fun pair(hand: List<Card>): Boolean =
        hand.groupBy(Card::value).any { it.value.size == 2 && ((it.key >= if (jacksOrBetter) 11 else 1) || it.key == 1) }

    private fun twoPair(hand: List<Card>): Boolean = hand.groupBy(Card::value).entries.count { it.value.size == 2 } == 2
    private fun threeOfAKind(hand: List<Card>) = hand.groupBy(Card::value).any { it.value.size == 3 }
    private fun fourOfAKind(hand: List<Card>): Boolean = hand.groupBy(Card::value).any { it.value.size == 4 }
    private fun fullHouse(hand: List<Card>): Boolean = threeOfAKind(hand) && hand.groupBy(Card::value).any { it.value.size == 2 }

    private fun straight(hand: List<Card>): Boolean {
        val h = hand.sortedBy(Card::value)
        for (i in 0 until h.size - 1) {
            var value = h[i].value
            if (value == 1) if (h[i + 1].value == 10) value = 9 //ace check
            if (value + 1 != h[i + 1].value) return false
        }
        return true
    }

    private fun flush(hand: List<Card>): Boolean = hand.all { it.suit == hand[0].suit }
    private fun straightFlush(h: List<Card>): Boolean = straight(h) && flush(h)

    private fun royalFlush(hand: List<Card>): Boolean {
        val h = hand.sortedBy(Card::value)
        if (h[1].value == 10)
            if (h[2].value == 11)
                if (h[3].value == 12)
                    if (h[4].value == 13)
                        if (h[0].value == 1)
                            if (straightFlush(hand)) return true
        return false
    }

    fun winCheck(h: List<Card>, bet: Int): Int = when {
        royalFlush(h) -> PokerHand.ROYAL_FLUSH
        straightFlush(h) -> PokerHand.STRAIGHT_FLUSH
        fourOfAKind(h) -> PokerHand.FOUR_KIND
        fullHouse(h) -> PokerHand.FULL_HOUSE
        flush(h) -> PokerHand.FLUSH
        straight(h) -> PokerHand.STRAIGHT
        threeOfAKind(h) -> PokerHand.THREE_KIND
        twoPair(h) -> PokerHand.TWO_PAIR
        pair(h) -> PokerHand.PAIR
        else -> null
    }?.let { getWinning(it, bet) } ?: -bet

    fun getWinningHand(h: List<Card>): PokerHand = when {
        royalFlush(h) -> PokerHand.ROYAL_FLUSH
        straightFlush(h) -> PokerHand.STRAIGHT_FLUSH
        fourOfAKind(h) -> PokerHand.FOUR_KIND
        fullHouse(h) -> PokerHand.FULL_HOUSE
        flush(h) -> PokerHand.FLUSH
        straight(h) -> PokerHand.STRAIGHT
        threeOfAKind(h) -> PokerHand.THREE_KIND
        twoPair(h) -> PokerHand.TWO_PAIR
        pair(h) -> PokerHand.PAIR
        else -> PokerHand.HIGH_CARD
    }

    private fun getWinning(rank: PokerHand, bet: Int): Int = rank.defaultWinning * bet
}

enum class PokerHand(val stringName: String, val defaultWinning: Int) {
    ROYAL_FLUSH("Royal Flush", 250),
    STRAIGHT_FLUSH("Straight Flush", 50),
    FOUR_KIND("Four of a Kind", 25),
    FULL_HOUSE("Full House", 9),
    FLUSH("Flush", 6),
    STRAIGHT("Straight", 4),
    THREE_KIND("Three of a Kind", 3),
    TWO_PAIR("Two Pair", 2),
    PAIR("Pair", 1),
    HIGH_CARD("High Card", 0)
}