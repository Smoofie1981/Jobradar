package net.therapietermin.jobradar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.therapietermin.jobradar.data.AppDatabase
import net.therapietermin.jobradar.data.Job
import net.therapietermin.jobradar.network.JobRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobradarApp() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).jobs() }
    val repository = remember { JobRepository(dao) }
    val scope = rememberCoroutineScope()
    val jobs by dao.observeAll().collectAsState(initial = emptyList())

    var tab by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Live-Suche: Magdeburg + 50 km, Stendal als Ausnahme") }

    val labels = listOf("Neu", "Interessant", "Beworben", "Ausgeblendet")
    val statuses = listOf("NEW", "INTERESTING", "APPLIED", "HIDDEN")
    val visibleJobs = jobs.filter { it.status == statuses[tab] }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Jobradar") }) },
        bottomBar = {
            NavigationBar {
                labels.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(listOf("●", "★", "✓", "×")[i]) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Button(
                    onClick = {
                        scope.launch {
                            searching = true
                            message = "Suche läuft …"
                            try {
                                val result = repository.refresh()
                                message = "${result.matched} passende Stellen gefunden · ${result.newCount} neu · ${result.scanned} geprüft"
                            } catch (e: Exception) {
                                message = "Suche fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
                            } finally {
                                searching = false
                            }
                        }
                    },
                    enabled = !searching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (searching) "Suche läuft …" else "Jetzt nach neuen Jobs suchen")
                }
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Quelle aktuell: Jobsuche der Bundesagentur für Arbeit · tägliche Hintergrundsuche aktiviert",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (visibleJobs.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            if (tab == 0) "Noch keine Treffer gespeichert. Starte oben die Live-Suche."
                            else "In diesem Bereich sind noch keine Stellen.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(visibleJobs, key = { it.sourceId }) { job ->
                JobCard(
                    job = job,
                    onOpen = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.url)))
                        }
                    },
                    onStatus = { status -> scope.launch { dao.setStatus(job.sourceId, status) } }
                )
            }
        }
    }
}

@Composable
private fun JobCard(job: Job, onOpen: () -> Unit, onStatus: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${job.score} % passend", style = MaterialTheme.typography.labelLarge)
            Text(job.title, style = MaterialTheme.typography.titleMedium)
            Text("${job.employer} · ${job.city}")
            job.pay?.let { Text(it) }
            if (job.permanent == true) Text("Unbefristet")
            if (job.reasons.isNotBlank()) Text(job.reasons, style = MaterialTheme.typography.bodySmall)
            Text(job.source, style = MaterialTheme.typography.labelSmall)

            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text("Stellenanzeige öffnen")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onStatus("NEW") }) { Text("Neu") }
                TextButton(onClick = { onStatus("INTERESTING") }) { Text("★") }
                TextButton(onClick = { onStatus("APPLIED") }) { Text("✓") }
                TextButton(onClick = { onStatus("HIDDEN") }) { Text("×") }
            }
        }
    }
}
