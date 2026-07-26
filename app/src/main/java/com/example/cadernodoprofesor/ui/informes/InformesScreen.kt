package com.example.cadernodoprofesor.ui.informes

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.cadernodoprofesor.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cadernodoprofesor.data.Alumno
import com.example.cadernodoprofesor.data.Asistencia
import com.example.cadernodoprofesor.data.Preferencias
import com.example.cadernodoprofesor.ui.calendario.CalendarioViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformesScreen(viewModel: CalendarioViewModel) {
    var selectedInformeTab by rememberSaveable { mutableIntStateOf(0) }
    val informeTabs = listOf("Asistencia", "Individual", "Centros", "Eventos")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedInformeTab) {
            informeTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedInformeTab == index,
                    onClick = { selectedInformeTab = index },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        when (selectedInformeTab) {
            0 -> InformeAsistencia(viewModel)
            1 -> InformeAltaUI(viewModel)
            2 -> InformeCentros(viewModel)
            3 -> InformeEventos(viewModel)
        }
    }
}

@Composable
fun InformeCentros(viewModel: CalendarioViewModel) {
    val prefs by viewModel.preferencias.collectAsState()
    var fechaInicio by rememberSaveable { mutableStateOf(LocalDate.now().minusMonths(1).toString()) }
    var fechaFin by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    
    val dateInicio = remember(fechaInicio) { LocalDate.parse(fechaInicio) }
    val dateFin = remember(fechaFin) { LocalDate.parse(fechaFin) }
    
    var aulaSeleccionada by rememberSaveable { mutableStateOf("HDDIJNP") }
    
    var showDatePickerInicio by remember { mutableStateOf(false) }
    var showDatePickerFin by remember { mutableStateOf(false) }

    val asistencias by viewModel.obtenerAsistenciaRango(dateInicio, dateFin, aulaSeleccionada).collectAsState(initial = emptyList())
    val alumnosActivos by viewModel.obtenerAlumnos(aulaSeleccionada, true).collectAsState(initial = emptyList())
    val alumnosHistoricos by viewModel.obtenerAlumnos(aulaSeleccionada, false).collectAsState(initial = emptyList())
    
    val centros = remember(asistencias, alumnosActivos, alumnosHistoricos) {
        val idsAlumnosPeriodo = asistencias.map { it.alumnoId }.toSet()
        (alumnosActivos + alumnosHistoricos)
            .filter { it.id in idsAlumnosPeriodo && it.centroEstudos.isNotBlank() }
            .map { it.centroEstudos }
            .distinct()
            .sorted()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Centros educativos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Row {
                IconButton(onClick = { viewModel.generarInformeCentros(centros, dateInicio, dateFin, aulaSeleccionada) }, enabled = centros.isNotEmpty()) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Xerar PDF", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.generarInformeODT("Centros", centros, dateInicio, dateFin, aulaSeleccionada) }, enabled = centros.isNotEmpty()) {
                    Icon(Icons.Default.Description, contentDescription = "Xerar ODT", tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Espazo / Aula", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentPrefs = prefs ?: Preferencias()
                            FilterChip(selected = aulaSeleccionada == "HDDIJNP", onClick = { aulaSeleccionada = "HDDIJNP" }, label = { Text(currentPrefs.espazo1Acronimo) })
                            FilterChip(selected = aulaSeleccionada == "USMIJHAC", onClick = { aulaSeleccionada = "USMIJHAC" }, label = { Text(currentPrefs.espazo2Acronimo) })
                            FilterChip(selected = aulaSeleccionada == "AMBAS", onClick = { aulaSeleccionada = "AMBAS" }, label = { Text("Ambas") })
                        }

                        Text("Destino dos informes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentPrefs = prefs ?: Preferencias()
                            FilterChip(selected = currentPrefs.destinoInformes == "BOX", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "BOX")) }, label = { Text("BoxAbalar") })
                            FilterChip(selected = currentPrefs.destinoInformes == "LOCAL", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "LOCAL")) }, label = { Text("Teléfono") })
                            FilterChip(selected = currentPrefs.destinoInformes == "AMBOS", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "AMBOS")) }, label = { Text("Ambos") })
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectorFecha(label = "Dende", fecha = dateInicio.toString(), onClick = { showDatePickerInicio = true }, modifier = Modifier.weight(1f))
                            SelectorFecha(label = "Ata", fecha = dateFin.toString(), onClick = { showDatePickerFin = true }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (centros.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("Sen datos de centros neste período", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            } else {
                items(centros) { centro ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                        Text(centro, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (showDatePickerInicio) DatePickerModal(initialDate = dateInicio, onDismiss = { showDatePickerInicio = false }, onConfirm = { fechaInicio = it.toString(); showDatePickerInicio = false })
    if (showDatePickerFin) DatePickerModal(initialDate = dateFin, onDismiss = { showDatePickerFin = false }, onConfirm = { fechaFin = it.toString(); showDatePickerFin = false })
}

@Composable
fun InformeEventos(viewModel: CalendarioViewModel) {
    val prefs by viewModel.preferencias.collectAsState()
    var fechaInicio by rememberSaveable { mutableStateOf(LocalDate.now().minusMonths(1).toString()) }
    var fechaFin by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    
    val dateInicio = remember(fechaInicio) { LocalDate.parse(fechaInicio) }
    val dateFin = remember(fechaFin) { LocalDate.parse(fechaFin) }
    
    var aulaSeleccionada by rememberSaveable { mutableStateOf("HDDIJNP") }
    
    var showDatePickerInicio by remember { mutableStateOf(false) }
    var showDatePickerFin by remember { mutableStateOf(false) }

    val eventos by viewModel.obtenerEventosRango(dateInicio, dateFin, aulaSeleccionada).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Listado de Eventos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Row {
                IconButton(onClick = { viewModel.generarInformeEventos(eventos, dateInicio, dateFin, aulaSeleccionada) }, enabled = eventos.isNotEmpty()) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Xerar PDF", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.generarInformeODT("Eventos", eventos, dateInicio, dateFin, aulaSeleccionada) }, enabled = eventos.isNotEmpty()) {
                    Icon(Icons.Default.Description, contentDescription = "Xerar ODT", tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Espazo / Aula", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentPrefs = prefs ?: Preferencias()
                            FilterChip(selected = aulaSeleccionada == "HDDIJNP", onClick = { aulaSeleccionada = "HDDIJNP" }, label = { Text(currentPrefs.espazo1Acronimo) })
                            FilterChip(selected = aulaSeleccionada == "USMIJHAC", onClick = { aulaSeleccionada = "USMIJHAC" }, label = { Text(currentPrefs.espazo2Acronimo) })
                            FilterChip(selected = aulaSeleccionada == "AMBAS", onClick = { aulaSeleccionada = "AMBAS" }, label = { Text("Ambas") })
                        }

                        Text("Destino dos informes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentPrefs = prefs ?: Preferencias()
                            FilterChip(selected = currentPrefs.destinoInformes == "BOX", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "BOX")) }, label = { Text("BoxAbalar") })
                            FilterChip(selected = currentPrefs.destinoInformes == "LOCAL", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "LOCAL")) }, label = { Text("Teléfono") })
                            FilterChip(selected = currentPrefs.destinoInformes == "AMBOS", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "AMBOS")) }, label = { Text("Ambos") })
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectorFecha(label = "Dende", fecha = dateInicio.toString(), onClick = { showDatePickerInicio = true }, modifier = Modifier.weight(1f))
                            SelectorFecha(label = "Ata", fecha = dateFin.toString(), onClick = { showDatePickerFin = true }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (eventos.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("Sen eventos neste período", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            } else {
                items(eventos.sortedBy { it.fecha }) { evento ->
                    ListItem(
                        headlineContent = { Text(evento.descripcion) },
                        supportingContent = { Text("${evento.fecha} - ${evento.tipoEvento}") },
                        overlineContent = { Text(evento.aulaId, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp) },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    )
                }
            }
        }
    }

    if (showDatePickerInicio) DatePickerModal(initialDate = dateInicio, onDismiss = { showDatePickerInicio = false }, onConfirm = { fechaInicio = it.toString(); showDatePickerInicio = false })
    if (showDatePickerFin) DatePickerModal(initialDate = dateFin, onDismiss = { showDatePickerFin = false }, onConfirm = { fechaFin = it.toString(); showDatePickerFin = false })
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformeAsistencia(viewModel: CalendarioViewModel) {
    val prefs by viewModel.preferencias.collectAsState()
    
    var fechaInicio by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(14).toString()) }
    var fechaFin by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var aulaSeleccionada by rememberSaveable { mutableStateOf("HDDIJNP") }
    
    val dateInicio = remember(fechaInicio) { LocalDate.parse(fechaInicio) }
    val dateFin = remember(fechaFin) { LocalDate.parse(fechaFin) }
    
    val asistenciaRango by viewModel.obtenerAsistenciaRango(dateInicio, dateFin, aulaSeleccionada).collectAsState(initial = emptyList())
    val alumnosActivos by viewModel.obtenerAlumnos(aulaSeleccionada, true).collectAsState(initial = emptyList())
    val alumnosHistoricos by viewModel.obtenerAlumnos(aulaSeleccionada, false).collectAsState(initial = emptySet())
    
    val totalAcumulado = alumnosActivos.size + alumnosHistoricos.size
    
    var showDatePickerInicio by remember { mutableStateOf(false) }
    var showDatePickerFin by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cajas de Activos e Acumulados en una sola linea
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Activos", style = MaterialTheme.typography.labelSmall)
                    Text("${alumnosActivos.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Acumulado", style = MaterialTheme.typography.labelSmall)
                    Text("$totalAcumulado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Gráfica ao principio
        Text("Asistencia por día", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        AsistenciaChart(asistenciaRango, dateInicio, dateFin)

        // Selectores de espazos e datas
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Espazo / Aula", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentPrefs = prefs ?: Preferencias()
                    FilterChip(selected = aulaSeleccionada == "HDDIJNP", onClick = { aulaSeleccionada = "HDDIJNP" }, label = { Text(currentPrefs.espazo1Acronimo) })
                    FilterChip(selected = aulaSeleccionada == "USMIJHAC", onClick = { aulaSeleccionada = "USMIJHAC" }, label = { Text(currentPrefs.espazo2Acronimo) })
                    FilterChip(selected = aulaSeleccionada == "AMBAS", onClick = { aulaSeleccionada = "AMBAS" }, label = { Text("Ambas") })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorFecha(label = "Dende", fecha = dateInicio.toString(), onClick = { showDatePickerInicio = true }, modifier = Modifier.weight(1f))
                    SelectorFecha(label = "Ata", fecha = dateFin.toString(), onClick = { showDatePickerFin = true }, modifier = Modifier.weight(1f))
                }
            }
        }

        // Destino dos informes
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Destino dos informes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentPrefs = prefs ?: Preferencias()
                    FilterChip(selected = currentPrefs.destinoInformes == "BOX", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "BOX")) }, label = { Text("BoxAbalar") })
                    FilterChip(selected = currentPrefs.destinoInformes == "LOCAL", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "LOCAL")) }, label = { Text("Teléfono") })
                    FilterChip(selected = currentPrefs.destinoInformes == "AMBOS", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "AMBOS")) }, label = { Text("Ambos") })
                }
            }
        }

        // Botóns de xerar informe
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { viewModel.generarInformeAsistencia(asistenciaRango, dateInicio, dateFin, aulaSeleccionada) },
                enabled = asistenciaRango.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PictureAsPdf, null)
                Spacer(Modifier.width(8.dp))
                Text("PDF")
            }
            Button(
                onClick = { viewModel.generarInformeODT("Asistencia", asistenciaRango, dateInicio, dateFin, aulaSeleccionada) },
                enabled = asistenciaRango.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Description, null)
                Spacer(Modifier.width(8.dp))
                Text("ODT")
            }
        }
    }

    if (showDatePickerInicio) {
        DatePickerModal(
            initialDate = dateInicio,
            onDismiss = { showDatePickerInicio = false },
            onConfirm = { fechaInicio = it.toString(); showDatePickerInicio = false }
        )
    }
    if (showDatePickerFin) {
        DatePickerModal(
            initialDate = dateFin,
            onDismiss = { showDatePickerFin = false },
            onConfirm = { fechaFin = it.toString(); showDatePickerFin = false }
        )
    }
}

@Composable
fun InformeAltaUI(viewModel: CalendarioViewModel) {
    var textoBusqueda by rememberSaveable { mutableStateOf("") }
    var fechaInicio by rememberSaveable { mutableStateOf(LocalDate.now().minusMonths(1).toString()) }
    var fechaFin by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    
    val dateInicio = remember(fechaInicio) { LocalDate.parse(fechaInicio) }
    val dateFin = remember(fechaFin) { LocalDate.parse(fechaFin) }
    
    var showDatePickerInicio by remember { mutableStateOf(false) }
    var showDatePickerFin by remember { mutableStateOf(false) }

    val todosAlumnosHD by viewModel.obtenerAlumnos("HDDIJNP", true).collectAsState(initial = emptyList())
    val todosAlumnosUS by viewModel.obtenerAlumnos("USMIJHAC", true).collectAsState(initial = emptyList())
    val histAlumnosHD by viewModel.obtenerAlumnos("HDDIJNP", false).collectAsState(initial = emptyList())
    val histAlumnosUS by viewModel.obtenerAlumnos("USMIJHAC", false).collectAsState(initial = emptyList())
    
    val alumnosFiltrados = remember(textoBusqueda, todosAlumnosHD, todosAlumnosUS, histAlumnosHD, histAlumnosUS) {
        (todosAlumnosHD + todosAlumnosUS + histAlumnosHD + histAlumnosUS)
            .filter { 
                it.nomeCompleto.contains(textoBusqueda, ignoreCase = true) ||
                it.aulaId.contains(textoBusqueda, ignoreCase = true) ||
                it.centroEstudos.contains(textoBusqueda, ignoreCase = true)
            }
    }

    var colaProcesamientoIds by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    var indiceActual by rememberSaveable { mutableIntStateOf(0) }

    if (colaProcesamientoIds.isEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Xerar informes individuais", style = MaterialTheme.typography.titleMedium)
            }
            
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectorFecha(label = "Dende", fecha = dateInicio.toString(), onClick = { showDatePickerInicio = true }, modifier = Modifier.weight(1f))
                    SelectorFecha(label = "Ata", fecha = dateFin.toString(), onClick = { showDatePickerFin = true }, modifier = Modifier.weight(1f))
                }
            }

            item {
                OutlinedTextField(
                    value = textoBusqueda,
                    onValueChange = { textoBusqueda = it },
                    label = { Text("Buscar alumno (Nome, Aula, Centro)...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            items(alumnosFiltrados) { alumno ->
                ListItem(
                    headlineContent = { Text(alumno.nomeCompleto) },
                    supportingContent = { Text("${alumno.curso} ${alumno.nivel} - ${alumno.aulaId} - ${alumno.centroEstudos}") },
                    modifier = Modifier.clickable { 
                        colaProcesamientoIds = listOf(alumno.id)
                        indiceActual = 0
                    }
                )
            }
        }
    } else {
        val currentId = colaProcesamientoIds[indiceActual]
        val todosAlumnos = todosAlumnosHD + todosAlumnosUS + histAlumnosHD + histAlumnosUS
        val alumnoActual = todosAlumnos.find { it.id == currentId }
        
        if (alumnoActual != null) {
            ValoracionInformeAlta(
                alumno = alumnoActual,
                fechaInicio = dateInicio,
                fechaFin = dateFin,
                viewModel = viewModel,
                onBack = { 
                    colaProcesamientoIds = emptyList()
                },
                onConfirm = { obj, comp, incT, incE, incO, incC, comm -> 
                    // Gardar valoracións na BDD
                    obj.forEach { (item, valo) -> viewModel.insertarValoracionInforme(alumnoActual.id, dateFin, item, valo, "OBXETIVO") }
                    comp.forEach { (item, valo) -> viewModel.insertarValoracionInforme(alumnoActual.id, dateFin, item, valo, "COMPETENCIA") }
                    
                    viewModel.generarInformeAlta(alumnoActual, obj, comp, dateInicio, dateFin, incT, incE, incO, incC, comm)
                    
                    if (indiceActual < colaProcesamientoIds.size - 1) {
                        indiceActual++
                    } else {
                        colaProcesamientoIds = emptyList()
                    }
                }
            )
        } else {
            colaProcesamientoIds = emptyList()
        }
    }

    if (showDatePickerInicio) {
        DatePickerModal(
            initialDate = dateInicio,
            onDismiss = { showDatePickerInicio = false },
            onConfirm = { fechaInicio = it.toString(); showDatePickerInicio = false }
        )
    }
    if (showDatePickerFin) {
        DatePickerModal(
            initialDate = dateFin,
            onDismiss = { showDatePickerFin = false },
            onConfirm = { fechaFin = it.toString(); showDatePickerFin = false }
        )
    }
}

@Composable
fun ValoracionInformeAlta(
    alumno: Alumno,
    fechaInicio: LocalDate,
    fechaFin: LocalDate,
    viewModel: CalendarioViewModel,
    onBack: () -> Unit,
    onConfirm: (Map<String, String>, Map<String, String>, Boolean, Boolean, Boolean, Boolean, String) -> Unit
) {
    val prefs by viewModel.preferencias.collectAsState()
    val esPrimaria = alumno.nivel == "Primaria"
    val objetivosItems = if (esPrimaria) OBXETIVOS_PRIMARIA else OBXETIVOS_SECUNDARIA
    val competenciasItems = if (esPrimaria) COMPETENCIAS_PRIMARIA else COMPETENCIAS_SECUNDARIA

    val objetivosRatings = remember { mutableStateMapOf<String, String>() }
    val competenciasRatings = remember { mutableStateMapOf<String, String>() }

    val valoracionsGuardadas by viewModel.obtenerValoracionsInforme(alumno.id, fechaFin).collectAsState(initial = emptyList())
    
    LaunchedEffect(valoracionsGuardadas) {
        if (valoracionsGuardadas.isNotEmpty()) {
            valoracionsGuardadas.forEach { v ->
                if (v.tipo == "OBXETIVO") objetivosRatings[v.item] = v.valoracion
                else if (v.tipo == "COMPETENCIA") competenciasRatings[v.item] = v.valoracion
            }
        }
    }
    
    var incluirTrabajo by remember { mutableStateOf(true) }
    var incluirEntregas by remember { mutableStateOf(true) }
    var incluirObjetivos by remember { mutableStateOf(true) }
    var incluirCompetencias by remember { mutableStateOf(true) }
    
    var expTrabajo by remember { mutableStateOf(false) }
    var expEntregas by remember { mutableStateOf(false) }
    var expObjetivos by remember { mutableStateOf(false) }
    var expCompetencias by remember { mutableStateOf(false) }
    var comentarios by rememberSaveable { mutableStateOf("") }

    val registros by viewModel.obtenerHistorialAcademico(alumno.id).collectAsState(initial = emptyList())
    val entregas by viewModel.obtenerTodasEntregasAlumno(alumno.id).collectAsState(initial = emptyList())
    val materias by viewModel.obtenerMateriasAlumno(alumno.id).collectAsState(initial = emptyList())

    val registrosFiltrados = remember(registros, fechaInicio, fechaFin) {
        registros.filter {
            try {
                val f = LocalDate.parse(it.fecha)
                !f.isBefore(fechaInicio) && !f.isAfter(fechaFin)
            } catch (_: Exception) { false }
        }.sortedByDescending { it.fecha }
    }
    
    val entregasFiltradas = remember(entregas, fechaInicio, fechaFin) {
        entregas.filter {
            try {
                val f = LocalDate.parse(it.fecha)
                !f.isBefore(fechaInicio) && !f.isAfter(fechaFin)
            } catch (_: Exception) { false }
        }.sortedByDescending { it.fecha }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val context = LocalContext.current
        val logoResId = remember(context) { context.resources.getIdentifier("logo_informe", "drawable", context.packageName) }
        
        if (logoResId != 0) {
            Image(
                painter = painterResource(id = logoResId),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentScale = ContentScale.Fit
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Column {
                Text("Informe: ${alumno.nomeCompleto}", style = MaterialTheme.typography.titleLarge)
                Text("Período: $fechaInicio - $fechaFin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Destino do informe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentPrefs = prefs ?: Preferencias()
                    FilterChip(selected = currentPrefs.destinoInformes == "BOX", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "BOX")) }, label = { Text("BoxAbalar") })
                    FilterChip(selected = currentPrefs.destinoInformes == "LOCAL", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "LOCAL")) }, label = { Text("Teléfono") })
                    FilterChip(selected = currentPrefs.destinoInformes == "AMBOS", onClick = { viewModel.actualizarPreferencias(currentPrefs.copy(destinoInformes = "AMBOS")) }, label = { Text("Ambos") })
                }
            }
        }

        Text("O Informe Incluirá:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Rexistro diario de traballo
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expTrabajo = !expTrabajo }) {
                Checkbox(checked = incluirTrabajo, onCheckedChange = { incluirTrabajo = it })
                Text("Rexistro diario de traballo", modifier = Modifier.weight(1f))
                Icon(if (expTrabajo) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expTrabajo) {
                Card(modifier = Modifier.padding(start = 32.dp, top = 4.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (registrosFiltrados.isEmpty()) Text("Sen rexistros neste período", style = MaterialTheme.typography.bodySmall)
                        registrosFiltrados.take(10).forEach { r ->
                            Text("${r.fecha}: ${r.traballoDia}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (registrosFiltrados.size > 10) Text("...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Rexistro diario de entregas
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expEntregas = !expEntregas }) {
                Checkbox(checked = incluirEntregas, onCheckedChange = { incluirEntregas = it })
                Text("Rexistro diario de entregas", modifier = Modifier.weight(1f))
                Icon(if (expEntregas) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expEntregas) {
                Card(modifier = Modifier.padding(start = 32.dp, top = 4.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (entregasFiltradas.isEmpty()) Text("Sen entregas neste período", style = MaterialTheme.typography.bodySmall)
                        entregasFiltradas.forEach { e ->
                            val mat = materias.find { it.id == e.materiaId }?.nombre ?: "Materia"
                            Text("${e.fecha} [$mat]: ${e.descripcion}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Obxectivos
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expObjetivos = !expObjetivos }) {
                Checkbox(checked = incluirObjetivos, onCheckedChange = { incluirObjetivos = it })
                Text("Grao de logo dos obxectivos Xerais da etapa", modifier = Modifier.weight(1f))
                Icon(if (expObjetivos) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expObjetivos) {
                Card(
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp).fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    RatingTable(items = objetivosItems, ratings = objetivosRatings)
                }
            }
        }

        // Competencias
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expCompetencias = !expCompetencias }) {
                Checkbox(checked = incluirCompetencias, onCheckedChange = { incluirCompetencias = it })
                Text("Grao de adquisición das competencias básicas", modifier = Modifier.weight(1f))
                Icon(if (expCompetencias) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expCompetencias) {
                Card(
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp).fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    RatingTable(items = competenciasItems, ratings = competenciasRatings)
                }
            }
        }

        // Comentarios
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Comentarios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = comentarios,
                onValueChange = { comentarios = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Escribe aquí calquera observación adicional para o informe...") },
                minLines = 3
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { onConfirm(objetivosRatings.toMap(), competenciasRatings.toMap(), incluirTrabajo, incluirEntregas, incluirObjetivos, incluirCompetencias, comentarios) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PictureAsPdf, null)
                Spacer(Modifier.width(8.dp))
                Text("PDF")
            }
            Button(
                onClick = { viewModel.generarInformeAltaODT(alumno, objetivosRatings.toMap(), competenciasRatings.toMap(), fechaInicio, fechaFin, incluirTrabajo, incluirEntregas, incluirObjetivos, incluirCompetencias, comentarios) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Description, null)
                Spacer(Modifier.width(8.dp))
                Text("ODT")
            }
        }
    }
}

@Composable
fun SelectorFecha(label: String, fecha: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = fecha,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        )
        // Capa invisible para capturar o click en todo o campo
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable(onClick = onClick)
        )
    }
}

@Composable
fun RatingTable(items: List<String>, ratings: MutableMap<String, String>) {
    val opciones = listOf("Poco", "Regular", "Adecuado", "Bo", "Excelente")
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // Cabecera opciones
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Spacer(modifier = Modifier.weight(1.5f))
            opciones.forEach { op ->
                Text(op, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
            }
        }

        items.forEachIndexed { index, item ->
            val bgColor = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item, modifier = Modifier.weight(1.5f).padding(start = 4.dp), fontSize = 11.sp, lineHeight = 13.sp)
                opciones.forEach { op ->
                    RadioButton(
                        selected = ratings[item] == op,
                        onClick = { ratings[item] = op },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun AsistenciaChart(asistencias: List<Asistencia>, inicio: LocalDate, fin: LocalDate) {
    val diasLectivos = remember(inicio, fin) {
        val list = mutableListOf<LocalDate>()
        var curr = inicio
        while (!curr.isAfter(fin)) {
            if (curr.dayOfWeek.value < 6) { // Excluir Sabado (6) e Domingo (7)
                list.add(curr)
            }
            curr = curr.plusDays(1)
        }
        list
    }

    val counts = remember(asistencias, diasLectivos) {
        diasLectivos.map { dia -> asistencias.count { it.fecha == dia.toString() } }
    }

    val maxCount = remember(counts) { (counts.maxOrNull() ?: 1).coerceAtLeast(1) }
    val colorLinea = MaterialTheme.colorScheme.primary
    val colorPuntos = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val spacing = if (diasLectivos.size > 1) width / (diasLectivos.size - 1) else width

                val path = Path()
                
                if (counts.size > 1) {
                    for (i in 0 until counts.size - 1) {
                        val x1 = i * spacing
                        val y1 = height - (counts[i].toFloat() / maxCount.toFloat() * height)
                        val x2 = (i + 1) * spacing
                        val y2 = height - (counts[i + 1].toFloat() / maxCount.toFloat() * height)
                        
                        if (i == 0) path.moveTo(x1, y1)
                        
                        val controlX = (x1 + x2) / 2f
                        path.cubicTo(controlX, y1, controlX, y2, x2, y2)
                    }
                } else if (counts.size == 1) {
                    val y = height - (counts[0].toFloat() / maxCount.toFloat() * height)
                    path.moveTo(0f, y)
                    path.lineTo(width, y)
                }

                drawPath(
                    path = path,
                    color = colorLinea,
                    style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )

                // Dibujar puntos y etiquetas
                counts.forEachIndexed { index, count ->
                    val x = index * spacing
                    val y = height - (count.toFloat() / maxCount.toFloat() * height)
                    
                    drawCircle(
                        color = colorPuntos,
                        radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )

                    // Etiquetas de texto (Día y Valor)
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = with(density) { 10.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        
                        // Valor encima del punto
                        if (count > 0) {
                            drawContext.canvas.nativeCanvas.drawText(count.toString(), x, y - 10.dp.toPx(), paint)
                        }
                        
                        // Día debajo del eje X (Día do mes)
                        drawContext.canvas.nativeCanvas.drawText(diasLectivos[index].dayOfMonth.toString(), x, height + 15.dp.toPx(), paint)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initialDate: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let {
                    val date = java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    onConfirm(date)
                }
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) {
        DatePicker(state = datePickerState)
    }
}

val OBXETIVOS_PRIMARIA = listOf(
    "a) Convivencia, cidadanía e dereitos humanos.",
    "b) Hábitos de traballo, esforzo e espírito emprendedor.",
    "c) Resolución de conflitos e autonomía persoal.",
    "d) Igualdade, diversidade e non discriminación.",
    "e) Competencia lingüística en galego e castelán.",
    "f) Competencia básica en lingua estranxeira.",
    "g) Competencias matemáticas e cálculo básico.",
    "h) Ciencias, xeografía, historia e cultura.",
    "i) Competencias tecnolóxicas e uso crítico das TIC.",
    "j) Expresión artística e creación audiovisual.",
    "k) Saúde, hixiene, deporte e alimentación.",
    "l) Benestar animal e respecto polo medio.",
    "m) Desenvolvemento afectivo e prevención de estereotipos.",
    "n) Mobilidade activa, saudable e educación viaria.",
    "ñ) Cultura, lingua e identidade de Galicia."
)

val COMPETENCIAS_PRIMARIA = listOf(
    "a) Competencia en comunicación lingüística (CCL).",
    "b) Competencia plurilingüe (CP).",
    "c) Competencia matemática e competencia en ciencia, tecnoloxía e enxeñaría (STEM).",
    "d) Competencia dixital (CD).",
    "e) Competencia persoal, social e de aprender a aprender (CPSAA).",
    "f) Competencia cidadá (CC).",
    "g) Competencia emprendedora (CE).",
    "h) Competencia en conciencia e expresión culturais (CCEC)."
)

val OBXETIVOS_SECUNDARIA = listOf(
    "a) Cidadanía, dereitos humanos e igualdade.",
    "b) Hábitos de disciplina, estudo e traballo.",
    "c) Igualdade de xénero e non violencia.",
    "d) Desenvolvemento afectivo e resolución de conflitos.",
    "e) Xestión de información e tecnoloxías (TIC).",
    "f) Coñecemento e método científico.",
    "g) Espírito emprendedor e iniciativa persoal.",
    "h) Competencia lingüística e literatura (Galego/Castelán).",
    "i) Competencia en linguas estranxeiras.",
    "l) Historia, cultura e patrimonio artístico.",
    "m) Saúde, deporte, medio ambiente e sexualidade.",
    "n) Expresión e creación artística.",
    "ñ) Patrimonio e diversidade cultural de Galicia.",
    "o) Promoción e identidade da lingua galega."
)

val COMPETENCIAS_SECUNDARIA = listOf(
    "Comunicación lingüística (CCL).",
    "Competencia matemática e competencias básicas en ciencia e tecnoloxía (CMCCT).",
    "Competencia dixital (CD).",
    "Aprender a aprender (CAA).",
    "Competencias sociais e cívicas (CSC).",
    "Sentido de iniciativa e espíritu emprendedor (CSIEE).",
    "Conciencia e expresións culturais (CCEC)."
)
