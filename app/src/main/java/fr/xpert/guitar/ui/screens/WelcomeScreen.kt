package fr.xpert.guitar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.xpert.guitar.data.ProgressStore
import fr.xpert.guitar.ui.Nav
import fr.xpert.guitar.ui.components.PrimaryButton
import fr.xpert.guitar.ui.components.ScreenHeader
import fr.xpert.guitar.ui.components.XpertWordmark
import fr.xpert.guitar.ui.theme.X

/** Premier lancement : on demande le prénom. Aussi utilisé pour le changer depuis l'accueil. */
@Composable
fun WelcomeScreen(store: ProgressStore, nav: Nav) {
    val firstLaunch = remember { store.firstName.isEmpty() }
    var name by remember { mutableStateOf(store.firstName) }
    val valid = name.trim().isNotEmpty()
    fun save() {
        if (!valid) return
        store.firstName = name.trim().take(30)
        nav.back()
    }

    Column(Modifier.fillMaxSize()) {
        if (!firstLaunch) ScreenHeader("Mon prénom") { nav.back() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            if (firstLaunch) {
                XpertWordmark()
                Spacer(Modifier.height(32.dp))
                Text("Bienvenue !", style = MaterialTheme.typography.headlineLarge, color = X.Texte)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Avant de commencer, comment tu t'appelles ?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = X.Texte2,
                )
                Spacer(Modifier.height(24.dp))
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Ton prénom") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = X.Texte,
                    unfocusedTextColor = X.Texte,
                    focusedBorderColor = X.Orange,
                    unfocusedBorderColor = X.CarteBord,
                    focusedLabelColor = X.Jaune,
                    unfocusedLabelColor = X.Texte2,
                    cursorColor = X.Jaune,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(if (firstLaunch) "C'est parti !" else "Enregistrer", Modifier.fillMaxWidth()) { save() }
        }
    }
}
