package com.example.undercover

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class Player(
    val name: String,
    val role: Role,
    val word: String?,
    val power: Power? = null,
    val isEliminated: Boolean = false,
    val isPowerUsed: Boolean = false // For powers like Boomerang
)

enum class Role(val displayName: String) {
    CIVILIAN("Civil"),
    UNDERCOVER("Infiltré"),
    MR_WHITE("M. White")
}

enum class Power(val displayName: String, val description: String) {
    FOU_DE_JOIE("Le Fou de Joie", "Gagne 4 points supplémentaires s'il est éliminé en premier."),
    BOOMERANG("Le Boomerang", "La première fois que le Boomerang reçoit la majorité des votes, au lieu d'être éliminé, les votes contre lui rebondissent sur ceux qui les ont exprimés !"),
    DEESSE_JUSTICE("Déesse de la Justice", "En cas d'égalité des votes, elle décide qui est éliminé (même si elle a déjà été éliminée)."),
    FANTOME("Le Fantôme", "Peut encore voter même après avoir été éliminé !"),
    VENGEUSE("La Vengeuse", "Quand la Vengeuse est éliminée, elle peut éliminer quelqu'un avec elle (nécessite 5 joueurs ou plus).")
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
            wordPairs = listOf(WordPair("Chat", "Chien"))
        }
    }

    fun setupGame(context: Context, playerNames: List<String>, undercoverCount: Int, mrWhiteCount: Int, selectedPowers: Set<Power>): List<Player> {
        loadWords(context)

        val playerCount = playerNames.size
        val roles = mutableListOf<Role>()
        val civilianCount = playerCount - undercoverCount - mrWhiteCount

        repeat(civilianCount) { roles.add(Role.CIVILIAN) }
        repeat(undercoverCount) { roles.add(Role.UNDERCOVER) }
        repeat(mrWhiteCount) { roles.add(Role.MR_WHITE) }
        roles.shuffle()

        val powersToAssign = selectedPowers.shuffled().toMutableList()
        val playerIndices = (0 until playerCount).shuffled().toMutableList()
        val playerPowers = mutableMapOf<Int, Power>()

        while (powersToAssign.isNotEmpty() && playerIndices.isNotEmpty()) {
            playerPowers[playerIndices.removeAt(0)] = powersToAssign.removeAt(0)
        }

        val selectedWordPair = wordPairs.random()
        val (civilianWord, undercoverWord) = if (Math.random() > 0.5) selectedWordPair.word1 to selectedWordPair.word2 else selectedWordPair.word2 to selectedWordPair.word1

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