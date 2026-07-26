package com.example.cadernodoprofesor.ui.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cadernodoprofesor.data.Curso
import com.example.cadernodoprofesor.data.DiaCalendario
import com.example.cadernodoprofesor.data.Evento
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@Composable
fun CalendarioScreen(viewModel: CalendarioViewModel) {
    var tabSeleccionada by remember { mutableStateOf<CalendarioTab?>(null) }
    var diaSeleccionadoParaEvento by remember { mutableStateOf<LocalDate?>(null) }
    
    val localeGalego = remember { Locale.forLanguageTag("gl-ES") }
    val cursoActivo by viewModel.cursoActivo.collectAsState()
    val cursos by viewModel.cursos.collectAsState()
    val festivos by viewModel.festivos.collectAsState()
    val eventos by viewModel.eventos.collectAsState()
    
    val festivosMap = remember(festivos) { festivos.associateBy { it.fecha } }

    val hoy = LocalDate.now()
    val anioInicio = if (hoy.monthValue >= 9) hoy.year else hoy.year - 1
    val mesesEscolares = remember(anioInicio) {
        (9..12).map { YearMonth.of(anioInicio, it) } + (1..6).map { YearMonth.of(anioInicio + 1, it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = cursoActivo?.nombre ?: "Sen curso activo",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CalendarioTab.entries.forEach { tab ->
                val seleccionado = tabSeleccionada == tab
                Surface(
                    onClick = { tabSeleccionada = if (seleccionado) null else tab },
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    color = if (seleccionado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.height(56.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = tab.icono, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = tab.titulo, fontSize = 9.sp, fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal, color = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(mesesEscolares) { mes ->
                    MonthView(mes, localeGalego, festivosMap) { fecha -> diaSeleccionadoParaEvento = fecha }
                }
            }

            if (tabSeleccionada != null) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    when (tabSeleccionada) {
                        CalendarioTab.NOVO_CURSO -> FormularioNuevoCurso(onDismiss = { tabSeleccionada = null }, onConfirm = { n, i, f -> viewModel.insertarCurso(n, i, f); tabSeleccionada = null })
                        CalendarioTab.VACACIONS -> FormularioVacaciones(cursoActivo, festivos, onDismiss = { tabSeleccionada = null }, onConfirm = { n, i, f, u, cid -> viewModel.insertarFestivo(n, i, f, u, cid) }, onDelete = { viewModel.eliminarFestivosPorNombre(it, cursoActivo?.id ?: 0) })
                        CalendarioTab.SUCESO -> FormularioNuevoEvento("Suceso", cursoActivo, eventos.filter { it.tipoEvento == "Suceso" }, onDismiss = { tabSeleccionada = null }, onConfirm = { t, d, f, a -> viewModel.insertarEvento(t, d, f, a) }, onDelete = { viewModel.eliminarEvento(it) })
                        CalendarioTab.LISTADO -> ListadoCursos(cursos, cursoActivo?.id, onSelect = { viewModel.establecerCursoActivo(it) }, onDismiss = { tabSeleccionada = null }, onDelete = { viewModel.eliminarCurso(it) })
                        else -> {}
                    }
                }
            }
        }
    }

    if (diaSeleccionadoParaEvento != null) {
        DialogoNuevoEvento(fecha = diaSeleccionadoParaEvento!!, onDismiss = { diaSeleccionadoParaEvento = null }, onConfirm = { t, d, a -> viewModel.insertarEvento(t, d, diaSeleccionadoParaEvento!!, a); diaSeleccionadoParaEvento = null })
    }
}

@Composable
fun MonthView(month: YearMonth, locale: Locale, festivos: Map<String, DiaCalendario>, onDiaClick: (LocalDate) -> Unit) {
    Card(elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = month.month.getDisplayName(TextStyle.FULL, locale).capitalize(locale) + " ${month.year}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Lu", "Ma", "Mé", "Xo", "Ve", "Sá", "Do").forEach { dia ->
                    Text(text = dia, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = if (dia == "Sá" || dia == "Do") Color.Red else Color.Unspecified)
                }
            }
            val firstDayOfWeek = month.atDay(1).dayOfWeek.value
            val daysInMonth = month.lengthOfMonth()
            val rows = (daysInMonth + firstDayOfWeek - 1 + 6) / 7
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 1..7) {
                        val dayNum = row * 7 + col - (firstDayOfWeek - 1)
                        if (dayNum in 1..daysInMonth) {
                            val fecha = month.atDay(dayNum)
                            DayCell(dayNum, col >= 6, festivos.containsKey(fecha.toString()), fecha == LocalDate.now(), Modifier.weight(1f).clickable { onDiaClick(fecha) })
                        } else { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(day: Int, isWeekend: Boolean, esFestivo: Boolean, esHoy: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.aspectRatio(1f).padding(2.dp).then(when { esHoy -> Modifier.background(MaterialTheme.colorScheme.primary, CircleShape); esFestivo -> Modifier.background(Color.Yellow.copy(alpha = 0.3f), CircleShape); else -> Modifier }), contentAlignment = Alignment.Center) {
        Text(text = day.toString(), fontSize = 12.sp, fontWeight = if (esFestivo || esHoy) FontWeight.Bold else FontWeight.Normal, color = when { esHoy -> MaterialTheme.colorScheme.onPrimary; isWeekend || esFestivo -> Color.Red; else -> Color.Unspecified })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioNuevoCurso(onDismiss: () -> Unit, onConfirm: (String, LocalDate, LocalDate) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var inicio by remember { mutableStateOf(LocalDate.of(LocalDate.now().year, 9, 1)) }
    var fin by remember { mutableStateOf(LocalDate.of(LocalDate.now().year + 1, 6, 30)) }
    var showI by remember { mutableStateOf(false) }
    var showF by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Novo Curso", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = nombre, onValueChange = { if (it.length <= 15) nombre = it }, label = { Text("Nome do curso") }, supportingText = { Text("${nombre.length}/15", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { showI = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Inicio: $inicio") }
            Button(onClick = { showF = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Remate: $fin") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (nombre.isNotBlank()) onConfirm(nombre, inicio, fin) }, enabled = nombre.isNotBlank()) { Text("Gardar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    }
    if (showI) { val state = rememberDatePickerState(inicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()); DatePickerDialog(onDismissRequest = { showI = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { inicio = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showI = false }) { Text("OK") } }) { DatePicker(state = state) } }
    if (showF) { val state = rememberDatePickerState(fin.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()); DatePickerDialog(onDismissRequest = { showF = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { fin = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showF = false }) { Text("OK") } }) { DatePicker(state = state) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioVacaciones(cursoActivo: Curso?, festivos: List<DiaCalendario>, onDismiss: () -> Unit, onConfirm: (String, LocalDate, LocalDate?, Boolean, Long) -> Unit, onDelete: (String) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var esUnDia by remember { mutableStateOf(false) }
    var inicio by remember { mutableStateOf(LocalDate.now()) }
    var fin by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var showI by remember { mutableStateOf(false) }
    var showF by remember { mutableStateOf(false) }

    val grouped = remember(festivos) {
        festivos.filter { it.nombreFestivo != null }
            .groupBy { it.nombreFestivo!! }
            .map { (name, list) ->
                val dates = list.map { LocalDate.parse(it.fecha) }.sorted()
                Triple(name, if (dates.size > 1) "${dates.first()} / ${dates.last()}" else "${dates.first()}", dates.first())
            }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Vacacións Escolares", style = MaterialTheme.typography.headlineSmall)
            if (cursoActivo == null) {
                Text("Debes activar un curso primeiro", color = Color.Red); Button(onClick = onDismiss) { Text("Volver") }
            } else {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nome (ex: Nadal)") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = esUnDia, onCheckedChange = { esUnDia = it }); Text("1 día") }
                Button(onClick = { showI = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Dende: $inicio") }
                if (!esUnDia) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showF = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Ata: $fin") }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onConfirm(nombre, inicio, if (esUnDia) null else fin, esUnDia, cursoActivo.id); nombre = "" }, enabled = nombre.isNotBlank()) { Text("Gardar") }
                    TextButton(onClick = onDismiss) { Text("Pechar") }
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Listado de vacacións:", fontWeight = FontWeight.Bold)
            }
        }
        
        if (cursoActivo != null) {
            items(grouped) { (name, range, start) ->
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(range) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { nombre = name; inicio = start; esUnDia = !range.contains(" / ") }) { Icon(Icons.Default.Edit, null) }
                            IconButton(onClick = { onDelete(name) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        }
                    }
                )
            }
        }
    }
    
    if (showI) { val state = rememberDatePickerState(inicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()); DatePickerDialog(onDismissRequest = { showI = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { inicio = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showI = false }) { Text("OK") } }) { DatePicker(state = state) } }
    if (showF) { val state = rememberDatePickerState(fin.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()); DatePickerDialog(onDismissRequest = { showF = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { fin = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showF = false }) { Text("OK") } }) { DatePicker(state = state) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioNuevoEvento(tipo: String, cursoActivo: Curso?, eventos: List<Evento>, onDismiss: () -> Unit, onConfirm: (String, String, LocalDate, String) -> Unit, onDelete: (Evento) -> Unit) {
    var desc by remember { mutableStateOf("") }
    var aula by remember { mutableStateOf("AMBAS") }
    var fecha by remember { mutableStateOf(LocalDate.now()) }
    var showF by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Novo $tipo", style = MaterialTheme.typography.headlineSmall)
            if (cursoActivo == null) {
                Text("Debes activar un curso primeiro", color = Color.Red); Button(onClick = onDismiss) { Text("Volver") }
            } else {
                Button(onClick = { showF = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Data: $fecha") }
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descrición") }, modifier = Modifier.fillMaxWidth())
                Text("Espazo de traballo:")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("HDDIJNP", "USMIJHAC", "AMBAS").forEach { a ->
                        FilterChip(selected = aula == a, onClick = { aula = a }, label = { Text(a) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onConfirm(tipo, desc, fecha, aula); desc = "" }) { Text("Engadir") }
                    TextButton(onClick = onDismiss) { Text("Pechar") }
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Listado de ${tipo}s:", fontWeight = FontWeight.Bold)
            }
        }
        if (cursoActivo != null) {
            items(eventos) { ev ->
                ListItem(
                    headlineContent = { Text(ev.descripcion) },
                    supportingContent = { Text("${ev.fecha} (${ev.aulaId})") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { desc = ev.descripcion; fecha = LocalDate.parse(ev.fecha); aula = ev.aulaId }) { Icon(Icons.Default.Edit, null) }
                            IconButton(onClick = { onDelete(ev) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        }
                    }
                )
            }
        }
    }
    if (showF) { val state = rememberDatePickerState(fecha.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()); DatePickerDialog(onDismissRequest = { showF = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { fecha = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showF = false }) { Text("OK") } }) { DatePicker(state = state) } }
}

@Composable
fun ListadoCursos(cursos: List<Curso>, activoId: Long?, onSelect: (Long) -> Unit, onDismiss: () -> Unit, onDelete: (Curso) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        item { Text("Listado de cursos", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp)) }
        items(cursos) { curso ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSelect(curso.id) }.padding(vertical = 8.dp)) {
                RadioButton(selected = curso.id == activoId, onClick = { onSelect(curso.id) })
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(curso.nombre, fontWeight = FontWeight.Bold)
                    Text("${curso.fechaInicio} / ${curso.fechaFin}", fontSize = 11.sp)
                }
                IconButton(onClick = { onDelete(curso) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoNuevoEvento(fecha: LocalDate, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var tipo by remember { mutableStateOf("Curso") }
    var desc by remember { mutableStateOf("") }
    var aula by remember { mutableStateOf("AMBAS") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Novo Evento: $fecha") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("Curso", "Reunión", "Suceso", "Licencia").forEach { t ->
                    FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t) }, modifier = Modifier.padding(end = 4.dp))
                }
            }
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descrición") })
            if (tipo == "Suceso" || tipo == "Licencia") {
                Row {
                    listOf("HDDIJNP", "USMIJHAC", "AMBAS").forEach { a ->
                        FilterChip(selected = aula == a, onClick = { aula = a }, label = { Text(a) }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = { onConfirm(tipo, desc, aula) }) { Text("Engadir") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

fun String.capitalize(locale: Locale) = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
