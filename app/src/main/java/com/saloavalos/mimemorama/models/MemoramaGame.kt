package com.saloavalos.mimemorama.models

import com.saloavalos.mimemorama.utils.DEFAULT_ICONS

class MemoramaGame(
    private val boardSize: BoardSize,
    private val customImages: List<String>?
) {

    val cards: List<MemoramaCard>
    var numPairsFound = 0

    // this one to count number of moves
    private var numCardFlips = 0
    private var indexOfSingleSelectedCard: Int? = null

    init {
        if (customImages == null) {
            // Icons for cards, takes them and shuffles them
            val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
            val randomizedImages = (chosenImages + chosenImages).shuffled()
            cards = randomizedImages.map { MemoramaCard(it)}
        } else {
            val randomizedImages = (customImages + customImages).shuffled()
            // it.hashCode() is the identifier, the id (the image url converted to integer)
            cards = randomizedImages.map { MemoramaCard(it.hashCode(), it) }
        }
    }

    fun flipCard(position: Int): Boolean {
        numCardFlips++

        val card = cards[position]

        // Three cases:
        // 0 cards previously flipped over => flip over the selected card
        // 1 card previously flipped over => flip over the selected card + check if the images match
        // 2 cards previously flipped over => restore cards + flips over the selected card
        var foundMatch = false
        if (indexOfSingleSelectedCard == null) {
            // 0 or 2 cards previously flipped over
            restoreCards()
            indexOfSingleSelectedCard = position
        } else {
            // exactly 1 card previously flipped over
            // check if the image is matched
            foundMatch = checkForMatch(indexOfSingleSelectedCard!!, position)
            // the user has finished flipping the card, now there are not just 1 card flipped over
            indexOfSingleSelectedCard = null
        }

        // switch if was up put it down and vice versa
        card.isFaceUp = !card.isFaceUp
        return foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        // if these 2 cards are not a match
        if (cards[position1].identifier != cards[position2].identifier) {
            return false
        }

        // else those 2 are a match
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numPairsFound++
        return true
    }

    private fun restoreCards() {
        for (card in cards) {
            if (!card.isMatched) {
                card.isFaceUp = false
            }
        }
    }

    fun haveWonGame(): Boolean {
        // we know we won the game when the number of pairs is equal to the total number of pairs that are in this borad
        return numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        // grab the card at that position and check the value of "isFaceUp"
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        // it is divided by 2 because we count a move when a user has flipped over 2 cards
        return numCardFlips / 2
    }
}
