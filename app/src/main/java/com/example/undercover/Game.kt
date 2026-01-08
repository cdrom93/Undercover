package com.example.undercover

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

data class Player(
    val name: String,
    val role: Role,
    val word: String?,
    val power: Power? = null,
    val isEliminated: Boolean = false,
    val isPowerUsed: Boolean = false
)

enum class Role(val displayName: String) {
    CIVILIAN("Civil"),
    UNDERCOVER("Infiltré"),
    MR_WHITE("M. White")
}

enum class Power(val displayName: String, val description: String, val emoji: String) {
    FOU_DE_JOIE("Le Fou de Joie", "Gagne 4 points supplémentaires s'il est éliminé en premier.", "🃏"),
    BOOMERANG("Le Boomerang", "La première fois que le Boomerang reçoit la majorité des votes, au lieu d'être éliminé, son pouvoir le protège !", "🪃"),
    DEESSE_JUSTICE("Déesse de la Justice", "En cas d'égalité des votes, elle décide qui est éliminé.", "⚖️"),
    FANTOME("Le Fantôme", "Peut encore voter même après avoir été éliminé !", "👻"),
    VENGEUSE("La Vengeuse", "Quand la Vengeuse est éliminée, elle peut éliminer quelqu'un avec elle.", "🦸‍♀️")
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
    private const val HISTORY_PREFS = "undercover_history"
    private const val HISTORY_KEY = "played_words"
    private const val MAX_HISTORY = 100

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
            wordPairs = listOf(WordPair("Chat", "Chien"))
        }
    }

    private fun getHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(HISTORY_KEY, "")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
    }

    private fun saveToHistory(context: Context, word1: String, word2: String) {
        val history = getHistory(context).toMutableList()
        history.add(0, word1)
        history.add(0, word2)
        val limitedHistory = history.distinct().take(MAX_HISTORY * 2)
        val prefs = context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(HISTORY_KEY, limitedHistory.joinToString(";")).apply()
    }

    fun setupGame(
        context: Context,
        playerNames: List<String>,
        undercoverCount: Int,
        mrWhiteCount: Int,
        selectedPowers: Set<Power>,
        randomRoles: Boolean
    ): List<Player> {
        loadWords(context)
        val history = getHistory(context)

        val availablePairs = wordPairs.filter { pair ->
            pair.word1 !in history && pair.word2 !in history
        }.ifEmpty { wordPairs }

        val selectedWordPair = availablePairs.random()
        saveToHistory(context, selectedWordPair.word1, selectedWordPair.word2)

        val playerCount = playerNames.size
        var finalUndercoverCount = undercoverCount
        var finalMrWhiteCount = mrWhiteCount

        if (randomRoles) {
            val maxBad = playerCount / 2
            if (maxBad > 0) {
                val totalBad = (1..maxBad).random()
                val maxMrWhite = if (playerCount >= 5) totalBad else 0
                finalMrWhiteCount = (0..maxMrWhite).random()
                finalUndercoverCount = totalBad - finalMrWhiteCount
            } else {
                finalUndercoverCount = 0
                finalMrWhiteCount = 0
            }
        }

        val roles = mutableListOf<Role>()
        val civilianCount = playerCount - finalUndercoverCount - finalMrWhiteCount

        repeat(civilianCount) { roles.add(Role.CIVILIAN) }
        repeat(finalUndercoverCount) { roles.add(Role.UNDERCOVER) }
        repeat(finalMrWhiteCount) { roles.add(Role.MR_WHITE) }
        roles.shuffle()

        val powersToAssign = selectedPowers.shuffled().toMutableList()
        val playerIndices = (0 until playerCount).shuffled().toMutableList()
        val playerPowers = mutableMapOf<Int, Power>()

        while (powersToAssign.isNotEmpty() && playerIndices.isNotEmpty()) {
            playerPowers[playerIndices.removeAt(0)] = powersToAssign.removeAt(0)
        }

        val (civilianWord, undercoverWord) = if (Random.nextBoolean()) selectedWordPair.word1 to selectedWordPair.word2 else selectedWordPair.word2 to selectedWordPair.word1

        return playerNames.mapIndexed { index, name ->
            Player(
                name = name,
                role = roles[index],
                word = when (roles[index]) {
                    Role.CIVILIAN -> civilianWord
                    Role.UNDERCOVER -> undercoverWord
                    Role.MR_WHITE -> null
                },
                power = playerPowers[index]
            )
        }
    }

    fun checkWinner(players: List<Player>): Winner {
        val activePlayers = players.filter { !it.isEliminated }
        val activeCivilians = activePlayers.count { it.role == Role.CIVILIAN }
        val activeUndercovers = activePlayers.count { it.role == Role.UNDERCOVER || it.role == Role.MR_WHITE }

        if (activeUndercovers == 0) return Winner.CIVILIANS_WIN
        if (activeCivilians <= 1) return Winner.UNDERCOVERS_WIN

        return Winner.NONE
    }
}