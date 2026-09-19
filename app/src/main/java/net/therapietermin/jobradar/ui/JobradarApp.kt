package net.therapietermin.jobradar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.therapietermin.jobradar.data.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobradarApp() {
    var tab by remember { mutableIntStateOf(0) }
    val sample = listOf(
        Job("demo1","Demo","Projektsteuerung Infrastruktur","Beispiel-Arbeitgeber",
            "Magdeburg", pay="E12 / vergleichbar", permanent=true, url="#",
            score=92, reasons="Magdeburg • Projektsteuerung • Unbefristet • Gehaltsniveau passend"),
        Job("demo2","Demo","Technische Projektkoordination","Beispiel-Unternehmen",
            "Magdeburg", permanent=true, url="#", score=84,
            reasons="Technische Koordination • Magdeburg • Unbefristet")
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text("Jobradar") }) },
        bottomBar = {
            NavigationBar {
                listOf("Neu","Interessant","Beworben","Ausgeblendet").forEachIndexed { i, label ->
                    NavigationBarItem(selected=tab==i, onClick={tab=i},
                        icon={Text(listOf("●","★","✓","×")[i])}, label={Text(label)})
                }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                Button(onClick = {}, modifier=Modifier.fillMaxWidth()) { Text("Jetzt nach neuen Jobs suchen") }
            }
            items(sample) { job ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("${job.score} % passend", style=MaterialTheme.typography.labelLarge)
                        Text(job.title, style=MaterialTheme.typography.titleMedium)
                        Text("${job.employer} · ${job.city}")
                        job.pay?.let { Text(it) }
                        Text(job.reasons, style=MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick={}) { Text("★ Interessant") }
                            TextButton(onClick={}) { Text("✓ Beworben") }
                            TextButton(onClick={}) { Text("×") }
                        }
                    }
                }
            }
        }
    }
}
