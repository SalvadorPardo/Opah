package com.example.cadernodoprofesor.ui.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Star
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
import com.example.cadernodoprofesor.ui.theme.FunctionalColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

enum class CalendarioTab(val titulo: String, val icono: ImageVector) {
    NOVO_CURSO("+ Novo curso", Icons.AutoMirrored.Filled.MenuBook),
    VACACIONS("Vacacións", Icons.Default.BeachAccess),
    SUCESO("Evento", Icons.Default.Star),
    LISTADO("Listado de cursos", Icons.AutoMirrored.Filled.FormatListBulleted)
}

@Composable
fun CalendarioScreen(viewModel: CalendarioViewModel) {
    var tabSeleccionada by remember { mutableStateOf<CalendarioTab?>(null) }
    var diaSeleccionadoParaEvento by remember { mutableStateOf<LocalDate?>(null) }
    
    val localeGalego = remember { Locale.forLanguageTag("gl-ES") }
    
    val cursoActivo by viewModel.cursoActivo.collectAsState()
    val festivos by viewModel.festivos.collectAsState()
    val eventos by viewModel.eventos.collectAsState()
    val prefsNullable by viewModel.preferencias.collectAsState()
    val prefs = prefsNullable ?: com.example.cadernodoprofesor.data.Preferencias()

    val festivosMap = remember(festivos) { festivos.associateBy { it.fecha } }
    val eventosMap = remember(eventos) { eventos.groupBy { it.fecha } }
    val inicioCurso = remember(cursoActivo) { cursoActivo?.fechaInicio?.let { try { LocalDate.parse(it) } catch(e: Exception) { null } } }
    val finCurso = remember(cursoActivo) { cursoActivo?.fechaFin?.let { try { LocalDate.parse(it) } catch(e: Exception) { null } } }

    val mesesEscolares = remember(inicioCurso, finCurso) {
        if (inicioCurso != null && finCurso != null) {
            val start = YearMonth.from(inicioCurso)
            val end = YearMonth.from(finCurso)
            var current = start
            val list = mutableListOf<YearMonth>()
            while (!current.isAfter(end)) {
                list.add(current)
                current = current.plusMonths(1)
            }
            list
        } else {
            val hoy = LocalDate.now()
            val anioInicio = if (hoy.monthValue >= 9) hoy.year else hoy.year - 1
            (9..12).map { YearMonth.of(anioInicio, it) } + (1..6).map { YearMonth.of(anioInicio + 1, it) }
        }
    }

    val cursos by viewModel.cursos.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = cursoActivo?.nombre ?: "Sen curso activo",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(16.dp)
                .clickable { tabSeleccionada = null }
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
                        Icon(
                            imageVector = tab.icono,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tab.titulo,
                            fontSize = 9.sp,
                            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
                            color = if (seleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(mesesEscolares) { mes ->
                    MonthView(
                        month = mes,
                        locale = localeGalego,
                        festivos = festivosMap,
                        eventos = eventosMap,
                        inicioCurso = inicioCurso,
                        finCurso = finCurso
                    ) { fecha -> diaSeleccionadoParaEvento = fecha }
                }
            }

            if (tabSeleccionada != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                ) {
                    when (tabSeleccionada) {
                        CalendarioTab.NOVO_CURSO -> FormularioNuevoCurso(
                            onDismiss = { tabSeleccionada = null },
                            onConfirm = { nombre, inicio, fin -> viewModel.insertarCurso(nombre, inicio, fin); tabSeleccionada = null }
                        )
                        CalendarioTab.VACACIONS -> FormularioVacaciones(
                            cursoActivo = cursoActivo,
                            festivos = festivos,
                            onDismiss = { tabSeleccionada = null },
                            onConfirm = { nombre, inicio, fin, esUnDia, cursoId -> viewModel.insertarFestivo(nombre, inicio, fin, esUnDia, cursoId) },
                            onDelete = { viewModel.eliminarFestivosPorNombre(it, cursoActivo?.id ?: 0) }
                        )
                        CalendarioTab.SUCESO -> FormularioNuevoEvento(
                            tipoPredefinido = "Evento",
                            cursoActivo = cursoActivo,
                            eventos = eventos.filter { it.tipoEvento == "Suceso" || it.tipoEvento == "Evento" },
                            prefs = prefs,
                            onDismiss = { tabSeleccionada = null },
                            onConfirm = { tipo, desc, inicio, fin, aula -> viewModel.insertarEvento(tipo, desc, inicio, fin, aula) },
                            onDelete = { viewModel.eliminarEvento(it) }
                        )
                        CalendarioTab.LISTADO -> ListadoCursos(
                            cursos = cursos,
                            cursoActivoId = cursoActivo?.id,
                            onSelect = { viewModel.establecerCursoActivo(it) },
                            onDismiss = { tabSeleccionada = null },
                            onDelete = { viewModel.eliminarCurso(it) }
                        )
                        else -> {}
                    }
                }
            }
        }
    }

    if (diaSeleccionadoParaEvento != null) {
        DialogoNuevoEvento(
            fecha = diaSeleccionadoParaEvento!!,
            prefs = prefs,
            onDismiss = { diaSeleccionadoParaEvento = null },
            onConfirm = { tipo, desc, aula ->
                viewModel.insertarEvento(tipo, desc, diaSeleccionadoParaEvento!!, null, aula)
                diaSeleccionadoParaEvento = null
            }
        )
    }
}

@Composable
fun MonthView(
    month: YearMonth,
    locale: Locale,
    festivos: Map<String, DiaCalendario>,
    eventos: Map<String, List<Evento>>,
    inicioCurso: LocalDate?,
    finCurso: LocalDate?,
    onDiaClick: (LocalDate) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = month.month.getDisplayName(TextStyle.FULL, locale).capitalize(locale) + " ${month.year}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
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
                            val fechaStr = fecha.toString()
                            val isWeekend = col >= 6
                            val clickableModifier = if (!isWeekend) {
                                Modifier.clickable { onDiaClick(fecha) }
                            } else {
                                Modifier
                            }
                            
                            DayCell(
                                day = dayNum,
                                isWeekend = isWeekend,
                                esFestivo = festivos.containsKey(fechaStr),
                                esEvento = eventos.containsKey(fechaStr),
                                esInicioFin = fecha == inicioCurso || fecha == finCurso,
                                esHoy = fecha == LocalDate.now(),
                                modifier = Modifier.weight(1f).then(clickableModifier)
                            )
                        } else { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    isWeekend: Boolean,
    esFestivo: Boolean,
    esEvento: Boolean,
    esInicioFin: Boolean,
    esHoy: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        esHoy -> MaterialTheme.colorScheme.primary
        esInicioFin -> FunctionalColors.Blue.copy(alpha = 0.2f)
        esFestivo -> FunctionalColors.Orange.copy(alpha = 0.2f)
        esEvento -> FunctionalColors.Yellow.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            fontSize = 12.sp,
            fontWeight = if (esFestivo || esHoy || esInicioFin || esEvento) FontWeight.Bold else FontWeight.Normal,
            color = when {
                esHoy -> MaterialTheme.colorScheme.onPrimary
                isWeekend -> FunctionalColors.Red
                esFestivo -> FunctionalColors.Orange
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioNuevoCurso(onDismiss: () -> Unit, onConfirm: (String, LocalDate, LocalDate) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var inicio by remember { mutableStateOf(LocalDate.of(LocalDate.now().year, 9, 1)) }
    var fin by remember { mutableStateOf(LocalDate.of(LocalDate.now().year + 1, 6, 30)) }
    var showInicio by remember { mutableStateOf(false) }
    var showFin by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Novo Curso", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = nombre, onValueChange = { if (it.length <= 15) nombre = it }, label = { Text("Nome do curso") }, supportingText = { Text("${nombre.length}/15", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Box(modifier = Modifier.fillMaxWidth().clickable { showInicio = true }) { OutlinedTextField(value = inicio.toString(), onValueChange = {}, label = { Text("Data de inicio") }, readOnly = true, modifier = Modifier.fillMaxWidth(), enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline)) }
        Box(modifier = Modifier.fillMaxWidth().clickable { showFin = true }) { OutlinedTextField(value = fin.toString(), onValueChange = {}, label = { Text("Data de remate") }, readOnly = true, modifier = Modifier.fillMaxWidth(), enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { if (nombre.isNotBlank()) onConfirm(nombre, inicio, fin) }, enabled = nombre.isNotBlank()) { Text("Gardar") }; TextButton(onClick = onDismiss) { Text("Cancelar") } }
    }
    if (showInicio) {
        val state = rememberDatePickerState(initialSelectedDateMillis = inicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showInicio = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { inicio = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showInicio = false }) { Text("Aceptar") } }) { DatePicker(state = state) }
    }
    if (showFin) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = fin.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            initialDisplayedMonthMillis = inicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(onDismissRequest = { showFin = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { fin = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showFin = false }) { Text("Aceptar") } }) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioVacaciones(cursoActivo: Curso?, festivos: List<DiaCalendario>, onDismiss: () -> Unit, onConfirm: (String, LocalDate, LocalDate?, Boolean, Long) -> Unit, onDelete: (String) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var esUnDia by remember { mutableStateOf(false) }
    var inicio by remember { mutableStateOf(LocalDate.now()) }
    var fin by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var showInicio by remember { mutableStateOf(false) }
    var showFin by remember { mutableStateOf(false) }
    
    // Agrupamos os festivos por nome para o listado
    val festivosAgrupados = remember(festivos) {
        festivos.filter { it.nombreFestivo != null }
            .groupBy { it.nombreFestivo!! }
            .map { (nome, dias) ->
                val dates = dias.map { LocalDate.parse(it.fecha) }.sorted()
                nome to if (dates.size > 1) "${dates.first()} ao ${dates.last()}" else "${dates.first()}"
            }
    }

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Vacacións Escolares", style = MaterialTheme.typography.headlineSmall) }
        if (cursoActivo == null) { 
            item { Text("Debes activar un curso primeiro", color = Color.Red) }
            item { Button(onClick = onDismiss) { Text("Volver") } }
        }
        else {
            item { OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nome (ex: Nadal)") }, modifier = Modifier.fillMaxWidth()) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = esUnDia, onCheckedChange = { esUnDia = it }); Text("1 día") } }
            item { Box(modifier = Modifier.fillMaxWidth().clickable { showInicio = true }) { OutlinedTextField(value = inicio.toString(), onValueChange = {}, label = { Text("Data de inicio") }, readOnly = true, modifier = Modifier.fillMaxWidth(), enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline)) } }
            if (!esUnDia) { item { Box(modifier = Modifier.fillMaxWidth().clickable { showFin = true }) { OutlinedTextField(value = fin.toString(), onValueChange = {}, label = { Text("Data de remate") }, readOnly = true, modifier = Modifier.fillMaxWidth(), enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline)) } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = nombre.isNotBlank(), onClick = { onConfirm(nombre, inicio, if (esUnDia) null else fin, esUnDia, cursoActivo.id); nombre = "" }) { Text("Gardar") }; TextButton(onClick = onDismiss) { Text("Pechar") } } }
            item { HorizontalDivider() }
            item { Text("Vacacións rexistradas:", style = MaterialTheme.typography.titleMedium) }
            items(festivosAgrupados) { (nome, rango) ->
                ListItem(headlineContent = { Text(nome) }, supportingContent = { Text(rango) }, trailingContent = {
                    Row {
                        IconButton(onClick = { nombre = nome; esUnDia = !rango.contains(" ao ") }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                        IconButton(onClick = { onDelete(nome) }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red) }
                    }
                })
            }
        }
    }
    if (showInicio) { 
        val state = rememberDatePickerState(initialSelectedDateMillis = inicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()); 
        DatePickerDialog(onDismissRequest = { showInicio = false }, confirmButton = { TextButton(onClick = { 
            state.selectedDateMillis?.let { 
                inicio = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                if (fin.isBefore(inicio)) fin = inicio
            }; 
            showInicio = false 
        }) { Text("Aceptar") } }) { DatePicker(state = state) } 
    }
    if (showFin && !esUnDia) { 
        val state = rememberDatePickerState(
            initialSelectedDateMillis = fin.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            initialDisplayedMonthMillis = inicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        ); 
        DatePickerDialog(onDismissRequest = { showFin = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { fin = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showFin = false }) { Text("Aceptar") } }) { DatePicker(state = state) } 
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioNuevoEvento(
    tipoPredefinido: String,
    cursoActivo: Curso?,
    eventos: List<Evento>,
    prefs: com.example.cadernodoprofesor.data.Preferencias,
    onDismiss: () -> Unit,
    onConfirm: (String, String, LocalDate, LocalDate?, String) -> Unit,
    onDelete: (Evento) -> Unit
) {
    var desc by remember { mutableStateOf("") }
    var aula by remember { mutableStateOf("AMBAS") }
    var esUnDia by remember { mutableStateOf(true) }
    var inicio by remember { mutableStateOf(LocalDate.now()) }
    var fin by remember { mutableStateOf(LocalDate.now()) }
    var showInicio by remember { mutableStateOf(false) }
    var showFin by remember { mutableStateOf(false) }

    val opcionesAula = remember(prefs) {
        mutableListOf<String>().apply {
            if (prefs.espazo1Activo) add(prefs.espazo1Acronimo)
            if (prefs.espazo2Activo) add(prefs.espazo2Acronimo)
            if (prefs.espazo1Activo && prefs.espazo2Activo) add("AMBAS")
        }
    }
    
    if (aula !in opcionesAula && opcionesAula.isNotEmpty()) {
        aula = opcionesAula.first()
    }

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Novo $tipoPredefinido", style = MaterialTheme.typography.headlineSmall) }
        if (cursoActivo == null) {
            item { Text("Debes activar un curso primeiro", color = Color.Red) }
            item { Button(onClick = onDismiss) { Text("Volver") } }
        }
        else {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = esUnDia, onCheckedChange = { esUnDia = it }); Text("1 día") } }
            item { Box(modifier = Modifier.fillMaxWidth().clickable { showInicio = true }) { OutlinedTextField(value = inicio.toString(), onValueChange = {}, label = { Text(if (esUnDia) "Data" else "Data de inicio") }, readOnly = true, modifier = Modifier.fillMaxWidth(), enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline)) } }
            if (!esUnDia) {
                item { Box(modifier = Modifier.fillMaxWidth().clickable { showFin = true }) { OutlinedTextField(value = fin.toString(), onValueChange = {}, label = { Text("Data de remate") }, readOnly = true, modifier = Modifier.fillMaxWidth(), enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline)) } }
            }
            item { OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descrición") }, modifier = Modifier.fillMaxWidth()) }
            
            if (opcionesAula.isNotEmpty()) {
                item { Text("Espazo de traballo:") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { opcionesAula.forEach { a -> FilterChip(selected = aula == a, onClick = { aula = a }, label = { Text(a) }) } } }
            }
            
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { onConfirm(tipoPredefinido, desc, inicio, if (esUnDia) null else fin, aula); desc = "" }) { Text("Engadir") }; TextButton(onClick = onDismiss) { Text("Pechar") } } }
            item { HorizontalDivider() }
            item { Text("${tipoPredefinido}s rexistrados:", style = MaterialTheme.typography.titleMedium) }
            items(eventos) { evento ->
                ListItem(headlineContent = { Text(evento.descripcion) }, supportingContent = { Text("${evento.fecha} - ${evento.aulaId}") }, trailingContent = {
                    Row {
                        IconButton(onClick = { desc = evento.descripcion; inicio = LocalDate.parse(evento.fecha); aula = evento.aulaId }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                        IconButton(onClick = { onDelete(evento) }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red) }
                    }
                })
            }
        }
    }
    if (showInicio) {
        val state = rememberDatePickerState(initialSelectedDateMillis = inicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showInicio = false }, confirmButton = { TextButton(onClick = {
            state.selectedDateMillis?.let {
                inicio = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                if (fin.isBefore(inicio)) fin = inicio
            }
            showInicio = false
        }) { Text("Aceptar") } }) { DatePicker(state = state) }
    }
    if (showFin && !esUnDia) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = fin.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            initialDisplayedMonthMillis = inicio.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(onDismissRequest = { showFin = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { fin = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showFin = false }) { Text("Aceptar") } }) { DatePicker(state = state) }
    }
}

@Composable
fun ListadoCursos(cursos: List<Curso>, cursoActivoId: Long?, onSelect: (Long) -> Unit, onDismiss: () -> Unit, onDelete: (Curso) -> Unit) {
    LazyColumn(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
        item { Text("Listado de cursos", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp)) }
        items(cursos) { curso ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSelect(curso.id) }.padding(vertical = 8.dp)) {
                RadioButton(selected = curso.id == cursoActivoId, onClick = { onSelect(curso.id) })
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) { Text(curso.nombre, fontWeight = FontWeight.Bold); Text("${curso.fechaInicio} - ${curso.fechaFin}", style = MaterialTheme.typography.bodySmall) }
                IconButton(onClick = { onDelete(curso) }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red) }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Volver") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoNuevoEvento(
    fecha: LocalDate,
    prefs: com.example.cadernodoprofesor.data.Preferencias,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var tipo by remember { mutableStateOf("Curso") }
    var desc by remember { mutableStateOf("") }
    var aula by remember { mutableStateOf("AMBAS") }
    
    val opcionesAula = remember(prefs) {
        mutableListOf<String>().apply {
            if (prefs.espazo1Activo) add(prefs.espazo1Acronimo)
            if (prefs.espazo2Activo) add(prefs.espazo2Acronimo)
            if (prefs.espazo1Activo && prefs.espazo2Activo) add("AMBAS")
        }
    }
    
    if (aula !in opcionesAula && opcionesAula.isNotEmpty()) {
        aula = opcionesAula.first()
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Evento para o $fecha") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Curso", "Reunión", "Evento", "Licencia").forEach { t -> FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t, fontSize = 10.sp) }) } }
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Descrición") })
            if ((tipo == "Evento" || tipo == "Licencia") && opcionesAula.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { opcionesAula.forEach { a -> FilterChip(selected = aula == a, onClick = { aula = a }, label = { Text(a, fontSize = 10.sp) }) } }
            }
        }
    }, confirmButton = { Button(onClick = { onConfirm(tipo, desc, aula) }) { Text("Engadir") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

fun String.capitalize(locale: Locale) = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
