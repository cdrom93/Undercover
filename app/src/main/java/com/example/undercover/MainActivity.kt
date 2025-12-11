package com.example.undercover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.undercover.ui.theme.UndercoverTheme
import java.text.Normalizer

// --- State Definition ---

sealed class GameState {
    object Setup : GameState()
    data class NameEntry(val settings: GameSettings) : GameState()
    data class Reveal(val roundPlayers: List<Player>, val currentPlayerIndex: Int = 0, val isCardRevealed: Boolean = false) : GameState()
    data class Speaking(val roundPlayers: List<Player>, val speakingOrder: List<Player>) : GameState()
    data class Voting(val roundPlayers: List<Player>) : GameState()
    data class PlayerEliminated(val players: List<Player>, val eliminatedPlayer: Player, val isFirstElimination: Boolean) : GameState()
    data class AvengerRevenge(val players: List<Player>, val avenger: Player) : GameState()
    data class BoomerangEffect(val players: List<Player>, val boomerangPlayer: Player) : GameState()
    data class MrWhiteGuess(val players: List<Player>, val mrWhite: Player) : GameState()
    data class MrWhiteFailed(val players: List<Player>) : GameState()
    data class RoundOver(val winner: Winner, val originalPlayers: List<Player>, val firstEliminatedPlayer: Player?) : GameState()
    data class Scoreboard(val scores: Map<String, Int>) : GameState()
}

data class GameSettings(val playerCount: Int, val undercoverCount: Int, val mrWhiteCount: Int, val selectedPowers: Set<Power>)

// --- Main App Composable (Router) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UndercoverTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UndercoverApp()
                }
            }
        }
    }
}

@Composable
fun UndercoverApp() {
    var gameState by remember { mutableStateOf<GameState>(GameState.Setup) }
    var playerScores by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var gameSettings by remember { mutableStateOf<GameSettings?>(null) }
    val context = LocalContext.current

    when (val state = gameState) {
        is GameState.Setup -> {
            GameSetupScreen(onStartGame = { pCount, uCount, mWCount, sPowers ->
                val settings = GameSettings(pCount, uCount, mWCount, sPowers)
                gameSettings = settings
                gameState = GameState.NameEntry(settings)
            })
        }
        is GameState.NameEntry -> {
            NameEntryScreen(
                playerCount = state.settings.playerCount,
                onNamesConfirmed = { playerNames ->
                    gameSettings?.let {
                        val players = Game.setupGame(context, playerNames, it.undercoverCount, it.mrWhiteCount, it.selectedPowers)
                        playerScores = playerNames.associate { name -> name to 0 }
                        gameState = GameState.Reveal(players)
                    }
                }
            )
        }
        is GameState.Reveal -> {
            RevealScreen(
                state = state,
                onCardTap = { gameState = state.copy(isCardRevealed = true) },
                onNextPlayer = {
                    if (state.currentPlayerIndex == state.roundPlayers.size - 1) {
                        val speakingOrder = state.roundPlayers.shuffled().toMutableList()
                        gameState = GameState.Speaking(state.roundPlayers, speakingOrder)
                    } else {
                        gameState = state.copy(currentPlayerIndex = state.currentPlayerIndex + 1, isCardRevealed = false)
                    }
                }
            )
        }
        is GameState.Speaking -> {
            SpeakingScreen(
                state = state,
                onProceedToVote = { gameState = GameState.Voting(state.roundPlayers) }
            )
        }
        is GameState.Voting -> {
            VotingScreen(
                players = state.roundPlayers,
                onPlayerVoted = { votedPlayer ->
                    if (votedPlayer.power == Power.BOOMERANG && !votedPlayer.isPowerUsed) {
                        val updatedPlayers = state.roundPlayers.map {
                            if (it.name == votedPlayer.name) it.copy(isPowerUsed = true) else it
                        }
                        gameState = GameState.BoomerangEffect(updatedPlayers, votedPlayer)
                    } else {
                        val isFirstElimination = state.roundPlayers.none { it.isEliminated }
                        val updatedPlayers = state.roundPlayers.map {
                            if (it.name == votedPlayer.name) it.copy(isEliminated = true) else it
                        }
                        val newlyEliminatedPlayer = updatedPlayers.first { it.name == votedPlayer.name }
                        gameState = GameState.PlayerEliminated(updatedPlayers, newlyEliminatedPlayer, isFirstElimination)
                    }
                }
            )
        }
        is GameState.PlayerEliminated -> {
            PlayerEliminatedScreen(
                eliminatedPlayer = state.eliminatedPlayer,
                onContinue = {
                    if (state.eliminatedPlayer.power == Power.VENGEUSE && state.players.size >= 5) {
                        gameState = GameState.AvengerRevenge(state.players, state.eliminatedPlayer)
                    } else if (state.eliminatedPlayer.role == Role.MR_WHITE) {
                        gameState = GameState.MrWhiteGuess(state.players, state.eliminatedPlayer)
                    } else {
                        val winner = Game.checkWinner(state.players)
                        if (winner != Winner.NONE) {
                            val firstEliminated = if (state.isFirstElimination) state.eliminatedPlayer else null
                            gameState = GameState.RoundOver(winner, state.players, firstEliminated)
                        } else {
                            val speakingOrder = state.players.filter { !it.isEliminated }.shuffled()
                            gameState = GameState.Speaking(state.players, speakingOrder)
                        }
                    }
                }
            )
        }
        is GameState.AvengerRevenge -> {
            AvengerRevengeScreen(
                players = state.players,
                avenger = state.avenger,
                onPlayerChosen = { chosenPlayer ->
                    val updatedPlayers = state.players.map {
                        if (it.name == chosenPlayer.name) it.copy(isEliminated = true) else it
                    }
                    val newlyEliminatedPlayer = updatedPlayers.first { it.name == chosenPlayer.name }
                    gameState = GameState.PlayerEliminated(updatedPlayers, newlyEliminatedPlayer, false) // Not first elim
                }
            )
        }
        is GameState.BoomerangEffect -> {
            BoomerangEffectScreen(state.boomerangPlayer) {
                val speakingOrder = state.players.filter { !it.isEliminated }.shuffled()
                gameState = GameState.Speaking(state.players, speakingOrder)
            }
        }
        is GameState.MrWhiteGuess -> {
            MrWhiteGuessScreen(
                player = state.mrWhite,
                civilianWord = state.players.first { it.role == Role.CIVILIAN }.word!!,
                onGuess = { isCorrect ->
                    val winner = if (isCorrect) Winner.MR_WHITE_WINS else Game.checkWinner(state.players)
                    gameState = GameState.RoundOver(winner, state.players, null)
                }
            )
        }
        is GameState.MrWhiteFailed -> {
            MrWhiteFailedScreen(
                onContinue = {
                    val winner = Game.checkWinner(state.players)
                    if (winner != Winner.NONE) {
                        gameState = GameState.RoundOver(winner, state.players, null)
                    } else {
                        val speakingOrder = state.players.filter { !it.isEliminated }.shuffled()
                        gameState = GameState.Speaking(state.players, speakingOrder)
                    }
                }
            )
        }
        is GameState.RoundOver -> {
            RoundOverScreen(
                winner = state.winner,
                onShowScores = {
                    val newScores = playerScores.toMutableMap()
                    if (state.firstEliminatedPlayer?.power == Power.FOU_DE_JOIE) {
                        val jesterName = state.firstEliminatedPlayer.name
                        newScores[jesterName] = (newScores[jesterName] ?: 0) + 4
                    }

                    when (state.winner) {
                        Winner.CIVILIANS_WIN -> state.originalPlayers.filter { it.role == Role.CIVILIAN }.forEach { p -> newScores[p.name] = (newScores[p.name] ?: 0) + 2 }
                        Winner.UNDERCOVERS_WIN -> state.originalPlayers.filter { it.role != Role.CIVILIAN }.forEach { p -> newScores[p.name] = (newScores[p.name] ?: 0) + 10 }
                        Winner.MR_WHITE_WINS -> state.originalPlayers.find { it.role == Role.MR_WHITE }?.let { newScores[it.name] = (newScores[it.name] ?: 0) + 6 }
                        Winner.NONE -> {}
                    }
                    playerScores = newScores
                    gameState = GameState.Scoreboard(playerScores)
                }
            )
        }
        is GameState.Scoreboard -> {
            ScoreboardScreen(
                scores = state.scores,
                onContinue = {
                    gameSettings?.let {
                        val playerNames = playerScores.keys.toList()
                        val newRoundPlayers = Game.setupGame(context, playerNames, it.undercoverCount, it.mrWhiteCount, it.selectedPowers)
                        gameState = GameState.Reveal(newRoundPlayers)
                    }
                },
                onQuit = {
                    gameState = GameState.Setup
                    playerScores = emptyMap()
                }
            )
        }
    }
}

// --- Screen Composables ---

private val accentPattern = "[\\p{InCombiningDiacriticalMarks}]+".toRegex()

fun String.normalizeForComparison(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(accentPattern, "")
}

@Composable
fun NumberSelector(label: String, value: Int, onValueChange: (Int) -> Unit, minValue: Int, maxValue: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange(value - 1) }, enabled = value > minValue) { Icon(Icons.Filled.Remove, "Remove") }
            Text(text = "$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            IconButton(onClick = { onValueChange(value + 1) }, enabled = value < maxValue) { Icon(Icons.Filled.Add, "Add") }
        }
    }
}

@Composable
fun RulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Règles du jeu") },
        text = {
            LazyColumn {
                item {
                    Text("But du jeu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Démasquer les joueurs qui n'ont pas le même mot que vous !")
                    Spacer(Modifier.height(16.dp))
                    Text("Les Rôles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("\t• Civils: Ils sont majoritaires et ont tous le même mot. Leur but est de trouver et d'éliminer tous les Infiltrés et M. White.")
                    Text("\t• Infiltrés: Ils sont une minorité et ont un mot légèrement différent. Leur but est de se faire passer pour un Civil et de ne pas être éliminés.")
                    Text("\t• M. White: Il est seul et n'a aucun mot. Son but est de deviner le mot des Civils en écoutant les autres joueurs.")
                    Spacer(Modifier.height(16.dp))
                    Text("Déroulement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("1. Chacun découvre son mot (ou son rôle pour M. White).")
                    Text("2. À tour de rôle, chaque joueur donne un mot pour décrire le sien.")
                    Text("3. Après la discussion, le groupe vote pour éliminer un joueur.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}


@Composable
fun GameSetupScreen(onStartGame: (playerCount: Int, undercoverCount: Int, mrWhiteCount: Int, selectedPowers: Set<Power>) -> Unit) {
    var playerCount by remember { mutableIntStateOf(5) }
    var undercoverCount by remember { mutableIntStateOf(1) }
    var mrWhiteCount by remember { mutableIntStateOf(0) }
    var showRules by remember { mutableStateOf(false) }
    var selectedPowers by remember { mutableStateOf(emptySet<Power>()) }

    if (showRules) {
        RulesDialog { showRules = false }
    }

    val maxBadRoles = playerCount / 2
    if (undercoverCount + mrWhiteCount > maxBadRoles) {
        undercoverCount = 1.coerceAtMost(maxBadRoles)
        mrWhiteCount = 0
    }

    val badRolesCount = undercoverCount + mrWhiteCount
    val isRuleRespected = badRolesCount in 1..maxBadRoles && playerCount >= 3

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Undercover", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        Text("Configurez votre partie", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showRules = true }) { Text("Règles du jeu") }
        Spacer(Modifier.height(8.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NumberSelector("Joueurs", playerCount, { playerCount = it }, 3, 20)
                NumberSelector("Infiltrés", undercoverCount, { newValue ->
                    val newUndercover = newValue.coerceIn(0, maxBadRoles)
                    if (newUndercover + mrWhiteCount > maxBadRoles) mrWhiteCount = maxBadRoles - newUndercover
                    undercoverCount = newUndercover
                }, 0, maxBadRoles)
                NumberSelector("M. White", mrWhiteCount, { newValue ->
                    val newMrWhite = newValue.coerceIn(0, maxBadRoles)
                    if (newMrWhite + undercoverCount > maxBadRoles) undercoverCount = maxBadRoles - newMrWhite
                    mrWhiteCount = newMrWhite
                }, 0, maxBadRoles)
            }
        }
        Spacer(Modifier.height(16.dp))
        val civiliansCount = playerCount - badRolesCount
        Text("Civils: $civiliansCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Pouvoirs optionnels", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(Power.values()) { power ->
                val isEnabled = when (power) {
                    Power.VENGEUSE -> playerCount >= 5
                    else -> true
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = isEnabled) { selectedPowers = if (selectedPowers.contains(power)) selectedPowers - power else selectedPowers + power }
                ) {
                    Checkbox(checked = selectedPowers.contains(power), onCheckedChange = { isChecked ->
                        if (isChecked) selectedPowers += power else selectedPowers -= power
                    }, enabled = isEnabled)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(power.displayName, fontWeight = FontWeight.Bold, color = if(isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                        Text(power.description, style = MaterialTheme.typography.bodySmall, color = if(isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { onStartGame(playerCount, undercoverCount, mrWhiteCount, selectedPowers) }, enabled = isRuleRespected, modifier = Modifier.fillMaxWidth()) {
            Text("Suivant", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun NameEntryScreen(playerCount: Int, onNamesConfirmed: (List<String>) -> Unit) {
    val playerNames = remember { mutableStateListOf(*Array(playerCount) { "Joueur ${it + 1}" }) }
    val allNamesFilled = playerNames.all { it.isNotBlank() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Qui sont les joueurs ?", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(playerCount) { index ->
                OutlinedTextField(
                    value = playerNames[index],
                    onValueChange = { playerNames[index] = it },
                    label = { Text("Nom du joueur ${index + 1}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onNamesConfirmed(playerNames) }, enabled = allNamesFilled, modifier = Modifier.fillMaxWidth()) {
            Text("Commencer la partie", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun RevealScreen(state: GameState.Reveal, onCardTap: () -> Unit, onNextPlayer: () -> Unit) {
    val currentPlayer = state.roundPlayers[state.currentPlayerIndex]
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!state.isCardRevealed) {
            Text("${currentPlayer.name}, c'est votre tour.", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCardTap) { Text("Révéler votre mot") }
        } else {
            ElevatedCard(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (currentPlayer.power != null && currentPlayer.power != Power.DEESSE_JUSTICE) {
                        Text("Votre pouvoir : ${currentPlayer.power.displayName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(currentPlayer.power.description, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                    }

                    if (currentPlayer.role == Role.MR_WHITE) {
                        Text("Vous êtes M. White !", style = MaterialTheme.typography.headlineMedium)
                        Text("Votre but est de trouver le mot secret des civils.", textAlign = TextAlign.Center)
                    } else {
                        Text("Votre mot secret :", style = MaterialTheme.typography.headlineMedium)
                        Text(currentPlayer.word ?: "", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onNextPlayer) {
                Text(if (state.currentPlayerIndex == state.roundPlayers.size - 1) "Commencer le tour" else "Joueur suivant")
            }
        }
    }
}

@Composable
fun SpeakingScreen(state: GameState.Speaking, onProceedToVote: () -> Unit) {
    val deesse = state.roundPlayers.find { it.power == Power.DEESSE_JUSTICE }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tour de parole", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (deesse != null) {
            Text("La Déesse de la Justice est ${deesse.name}.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Text("Chaque joueur doit donner un mot pour décrire son mot secret. L'ordre de parole est le suivant :", textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        ElevatedCard(modifier = Modifier.weight(1f).fillMaxWidth()){
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(state.speakingOrder) { player ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(player.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                        if (player.power == Power.BOOMERANG && player.isPowerUsed) {
                            Text(" 🪃", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onProceedToVote, modifier = Modifier.fillMaxWidth()) { Text("Passer à l'élimination") }
    }
}

@Composable
fun VotingScreen(players: List<Player>, onPlayerVoted: (Player) -> Unit) {
    val activePlayers = players.filter { !it.isEliminated }
    var playerToConfirm by remember { mutableStateOf<Player?>(null) }

    playerToConfirm?.let { player ->
        AlertDialog(
            onDismissRequest = { playerToConfirm = null },
            title = { Text("Confirmer l'élimination") },
            text = { Text("Êtes-vous sûr de vouloir éliminer ${player.name} ?") },
            confirmButton = { Button(onClick = { onPlayerVoted(player); playerToConfirm = null }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { playerToConfirm = null }) { Text("Annuler") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Élimination", style = MaterialTheme.typography.headlineMedium)
        Text("Qui le groupe a-t-il décidé d'éliminer ?", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(activePlayers) { player ->
                Button(onClick = { playerToConfirm = player }, modifier = Modifier.fillMaxWidth()) {
                    Text(player.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun PlayerEliminatedScreen(eliminatedPlayer: Player, onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("${eliminatedPlayer.name} a été éliminé(e).", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Son rôle était : ${eliminatedPlayer.role.displayName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        eliminatedPlayer.power?.let {
            Spacer(Modifier.height(16.dp))
            Text("Son pouvoir était : ${it.displayName}", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onContinue) { Text("Continuer") }
    }
}

@Composable
fun AvengerRevengeScreen(players: List<Player>, avenger: Player, onPlayerChosen: (Player) -> Unit) {
    val potentialVictims = players.filter { !it.isEliminated && it.name != avenger.name }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Vengeance !", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("${avenger.name}, vous avez été éliminé(e), mais vous pouvez emporter quelqu'un avec vous.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text("Qui choisissez-vous ?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(potentialVictims) { victim ->
                Button(onClick = { onPlayerChosen(victim) }, modifier = Modifier.fillMaxWidth()) {
                    Text(victim.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun BoomerangEffectScreen(boomerangPlayer: Player, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("BOOMERANG !", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("${boomerangPlayer.name} utilise son pouvoir !", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("L'élimination est annulée pour cette fois. Une icône 🪃 apparaît à côté de son nom.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onContinue) { Text("Continuer") }
    }
}


@Composable
fun MrWhiteGuessScreen(player: Player, civilianWord: String, onGuess: (Boolean) -> Unit) {
    var guess by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("${player.name}, vous avez été démasqué !", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text("Devinez le mot des civils pour gagner !", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = guess, onValueChange = { guess = it }, label = { Text("Le mot secret") }, singleLine = true)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onGuess(guess.normalizeForComparison().equals(civilianWord.normalizeForComparison(), ignoreCase = true)) }) { Text("Valider") }
    }
}

@Composable
fun MrWhiteFailedScreen(onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Ce n'est pas le bon mot !", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text("M. White a échoué. La partie continue.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onContinue) { Text("Continuer") }
    }
}

@Composable
fun RoundOverScreen(winner: Winner, onShowScores: () -> Unit) {
    val winnerMessage = when (winner) {
        Winner.CIVILIANS_WIN -> "Les Civils remportent la manche !"
        Winner.UNDERCOVERS_WIN -> "Les Infiltrés & M. White remportent la manche !"
        Winner.MR_WHITE_WINS -> "M. White a deviné le mot et remporte la manche !"
        Winner.NONE -> "Erreur: Pas de gagnant"
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(winnerMessage, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onShowScores) { Text("Voir les scores") }
    }
}

@Composable
fun ScoreboardScreen(scores: Map<String, Int>, onContinue: () -> Unit, onQuit: () -> Unit) {
    val sortedScores = scores.toList().sortedByDescending { it.second }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Tableau des scores", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        ElevatedCard(modifier = Modifier.weight(1f).fillMaxWidth()){
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(sortedScores) { (name, score) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.titleLarge)
                        Text("$score points", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onQuit) { Text("Quitter") }
            Button(onClick = onContinue) { Text("Manche suivante") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GameSetupScreenPreview() {
    UndercoverTheme {
        GameSetupScreen(onStartGame = { _, _, _, _ -> })
    }
}
