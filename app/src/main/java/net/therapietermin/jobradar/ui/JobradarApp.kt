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
import net.therapietermin.jobradar.domain.JobMatcher
import net.therapietermin.jobradar.domain.RequirementParser
import net.therapietermin.jobradar.domain.SalaryParser
import net.therapietermin.jobradar.domain.SearchPreferences
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

    var selectedRadius by remember {
        mutableIntStateOf(SearchPreferences.getRadius(context))
    }
    var message by remember {
        mutableStateOf(
            "Suche: Magdeburg + $selectedRadius km · Stendal als Ausnahme"
        )
    }

    var selectedField by remember { mutableStateOf("Alle Fachbereiche") }
    var selectedEmployer by remember { mutableStateOf("Alle Arbeitgeber") }

    val labels = listOf("Neu", "Interessant", "Beworben", "Ausgeblendet")
    val statuses = listOf("NEW", "INTERESTING", "APPLIED", "HIDDEN")

    val eligibleInTab = jobs
        .filter { it.status == statuses[tab] }
        .filter { JobMatcher.shouldInclude(it) }

    val employers = remember(eligibleInTab, selectedField) {
        eligibleInTab
            .asSequence()
            .filter {
                selectedField == "Alle Fachbereiche" ||
                    JobMatcher.category(it) == selectedField
            }
            .map { it.employer.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }
            .toList()
    }

    val visibleJobs = eligibleInTab
        .filter {
            selectedField == "Alle Fachbereiche" ||
                JobMatcher.category(it) == selectedField
        }
        .filter {
            selectedEmployer == "Alle Arbeitgeber" ||
                it.employer == selectedEmployer
        }

    LaunchedEffect(selectedField, tab) {
        if (selectedEmployer != "Alle Arbeitgeber" &&
            selectedEmployer !in employers
        ) {
            selectedEmployer = "Alle Arbeitgeber"
        }
    }

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
                            message =
                                "Mehrquellen-Suche läuft · Radius $selectedRadius km …"
                            try {
                                val result = repository.refresh(selectedRadius)
                                message =
                                    "${result.accepted} Stellen übernommen · " +
                                    "${result.newCount} neu · ${result.scanned} geprüft\n" +
                                    "Quellen: ${result.sourceSummary}"
                            } catch (e: Exception) {
                                message =
                                    "Suche fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
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
                    Text(
                        if (searching) "Mehrquellen-Suche läuft …"
                        else "Jetzt nach neuen Jobs suchen"
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))

                FilterDropdown(
                    label = "Suchradius um Magdeburg",
                    value = "$selectedRadius km",
                    options = listOf("25 km", "50 km", "75 km", "100 km"),
                    onSelected = { value ->
                        val radius = value.substringBefore(" ").toIntOrNull() ?: 50
                        selectedRadius = radius
                        SearchPreferences.setRadius(context, radius)
                        message =
                            "Suche: Magdeburg + $radius km · Stendal als Ausnahme"
                    }
                )

                Spacer(Modifier.height(8.dp))

                FilterDropdown(
                    label = "Fachbereich",
                    value = selectedField,
                    options = listOf(
                        "Alle Fachbereiche",
                        "Technik",
                        "Medien",
                        "Sozialpädagogik"
                    ),
                    onSelected = {
                        selectedField = it
                        selectedEmployer = "Alle Arbeitgeber"
                    }
                )

                Spacer(Modifier.height(8.dp))

                FilterDropdown(
                    label = "Arbeitgeber / ausschreibende Stelle",
                    value = selectedEmployer,
                    options = listOf("Alle Arbeitgeber") + employers,
                    onSelected = { selectedEmployer = it }
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "${visibleJobs.size} Stellen in dieser Ansicht",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "Gehaltsregel: explizit unter 2.600 € netto/Monat wird ausgeschlossen. " +
                        "Bruttoangaben werden nicht automatisch umgerechnet.",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (visibleJobs.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            if (eligibleInTab.isEmpty())
                                "In diesem Bereich sind noch keine Stellen."
                            else
                                "Für diese Filterkombination gibt es keine Stellen.",
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
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(job.url))
                            )
                        }
                    },
                    onStatus = { status ->
                        scope.launch { dao.setStatus(job.sourceId, status) }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.distinct().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun JobCard(
    job: Job,
    onOpen: () -> Unit,
    onStatus: (String) -> Unit
) {
    val field = JobMatcher.category(job) ?: "Sonstiges"
    val salary = SalaryParser.displayFor(job)
    val requirements = RequirementParser.mandatory(job, maxItems = 4)

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(field, style = MaterialTheme.typography.labelLarge)
            Text(job.title, style = MaterialTheme.typography.titleMedium)
            Text("${job.employer} · ${job.city}")

            Text(
                "Vergütung: $salary",
                style = MaterialTheme.typography.bodyMedium
            )

            if (job.permanent == true) {
                Text("Unbefristet")
            }

            Spacer(Modifier.height(2.dp))
            Text(
                "Berufsvoraussetzungen:",
                style = MaterialTheme.typography.labelLarge
            )

            if (requirements.isEmpty()) {
                Text(
                    "• Keine eindeutigen Muss-Voraussetzungen aus der Anzeige erkannt",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                requirements.forEach { requirement ->
                    Text(
                        "• $requirement",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                "Quelle: ${job.source}",
                style = MaterialTheme.typography.labelSmall
            )

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
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
