package com.example.undercover

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class Player(val name: String, val role: Role, val word: String?, val isEliminated: Boolean = false)

enum class Role(val displayName: String) {
    CIVILIAN("Civil"),
    UNDERCOVER("Infiltré"),
    MR_WHITE("M. White")
}

enum class Winner {
    CIVILIANS_WIN,
    UNDERCOVERS_WIN,
    MR_WHITE_WINS,
    NONE
}

data class WordPair(val word1: String, val word2: String)

object Game {
    private var wordPairs: List<WordPair> = emptyList()

    private fun loadWords(context: Context) {
        if (wordPairs.isNotEmpty()) return

        val newWordPairs = mutableListOf<WordPair>()
        try {
            val inputStream = context.assets.open("word_pairs.csv")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.forEachLine { line ->
                val words = line.split(",")
                if (words.size == 2) {
                    newWordPairs.add(WordPair(words[0].trim(), words[1].trim()))
                }
            }
            wordPairs = newWordPairs
        } catch (e: Exception) {
            // In case of error (e.g. file not found), use a default list
            wordPairs = listOf(WordPair("Chat", "Chien"))
        }
    }

    fun setupGame(context: Context, playerNames: List<String>, undercoverCount: Int, mrWhiteCount: Int): List<Player> {
        loadWords(context)

        val playerCount = playerNames.size
        val roles = mutableListOf<Role>()
        val civilianCount = playerCount - undercoverCount - mrWhiteCount

        repeat(civilianCount) { roles.add(Role.CIVILIAN) }
        repeat(undercoverCount) { roles.add(Role.UNDERCOVER) }
        repeat(mrWhiteCount) { roles.add(Role.MR_WHITE) }
        roles.shuffle()

        val selectedWordPair = wordPairs.random()
        val (civilianWord, undercoverWord) = if (Math.random() > 0.5) {
            selectedWordPair.word1 to selectedWordPair.word2
        } else {
            selectedWordPair.word2 to selectedWordPair.word1
        }

        return playerNames.mapIndexed { index, name ->
            val role = roles[index]
            val word = when (role) {
                Role.CIVILIAN -> civilianWord
                Role.UNDERCOVER -> undercoverWord
                Role.MR_WHITE -> null
            }
            Player(name = name, role = role, word = word)
        }
    }

    fun checkWinner(players: List<Player>): Winner {
        val activePlayers = players.filter { !it.isEliminated }
        val activeCivilians = activePlayers.count { it.role == Role.CIVILIAN }
        val activeUndercovers = activePlayers.count { it.role == Role.UNDERCOVER || it.role == Role.MR_WHITE }

        if (activeUndercovers == 0) {
            return Winner.CIVILIANS_WIN
        }

        if (activeCivilians <= 1) {
            return Winner.UNDERCOVERS_WIN
        }

        return Winner.NONE
    }
}