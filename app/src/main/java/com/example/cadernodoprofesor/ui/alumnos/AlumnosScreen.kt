package com.example.cadernodoprofesor.ui.alumnos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.cadernodoprofesor.data.Alumno
import com.example.cadernodoprofesor.data.MateriaAlumno
import com.example.cadernodoprofesor.data.RegistroAcademico
import com.example.cadernodoprofesor.data.EntregaTrabajo
import com.example.cadernodoprofesor.data.Asistencia
import com.example.cadernodoprofesor.data.NotaAlumno
import com.example.cadernodoprofesor.ui.calendario.CalendarioViewModel
import com.example.cadernodoprofesor.ui.theme.FunctionalColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AlumnosScreen(
    viewModel: CalendarioViewModel, 
    aulaId: String,
    alumnoIdInicial: Long? = null,
    onDetalleCerrado: (() -> Unit)? = null
) {
    val hoy = remember { LocalDate.now() }
    var fechaRefString by rememberSaveable { mutableStateOf(hoy.toString()) }
    val fechaReferencia = remember(fechaRefString) { LocalDate.parse(fechaRefString) }
    
    val alumnosEstado by viewModel.obtenerAlumnosConEstadoEnFecha(aulaId, fechaReferencia).collectAsState(initial = Pair(emptyList(), emptyList()))
    val alumnosActivos = alumnosEstado.first
    val alumnosHistoricos = alumnosEstado.second
    
    val asistenciaDia by viewModel.obtenerAsistencia(fechaReferencia, aulaId).collectAsState(initial = emptyList())
    val aulaDiaria by viewModel.obtenerAulaDiaria(aulaId, fechaReferencia).collectAsState(initial = null)
    
    var alumnoIdDetalle by rememberSaveable(alumnoIdInicial) { mutableStateOf(alumnoIdInicial) }
    val alumnoDetalle = remember(alumnoIdDetalle, alumnosActivos, alumnosHistoricos) { 
        alumnosActivos.find { it.id == alumnoIdDetalle } ?: alumnosHistoricos.find { it.id == alumnoIdDetalle }
    }
    
    var mostrandoAlta by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var historicoExpandido by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = Triple(alumnoDetalle, mostrandoAlta, fechaReferencia),
            transitionSpec = {
                if (targetState.first != null || targetState.second) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "NavegacionAlumnos"
        ) { (detalle, alta, fecha) ->
            when {
                detalle != null -> {
                    DetalleAlumno(
                        alumno = detalle,
                        viewModel = viewModel,
                        fechaReferencia = fecha,
                        onBack = { 
                            alumnoIdDetalle = null
                            onDetalleCerrado?.invoke()
                        },
                        onBaja = { 
                            viewModel.cambiarEstadoAlumno(detalle.id, false, fecha)
                            alumnoIdDetalle = null
                            onDetalleCerrado?.invoke()
                        },
                        onUpload = { uris -> viewModel.subirDocumentacionAlumno(detalle, uris) },
                        onSave = { updated -> viewModel.insertarAlumno(updated) }
                    )
                }
                alta -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { mostrandoAlta = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                            Text(text = "Novo Alumno", style = MaterialTheme.typography.headlineMedium)
                        }
                        FormularioAlumno(
                            fechaDefault = fecha,
                            alumnosActivos = alumnosActivos,
                            alumnosHistoricos = alumnosHistoricos,
                            onReactivar = { alumno ->
                                viewModel.cambiarEstadoAlumno(alumno.id, true, fecha)
                                mostrandoAlta = false
                            },
                            onGuardar = { alumno, materias ->
                                viewModel.insertarAlumnoConMaterias(alumno.copy(aulaId = aulaId, fechaCreacion = hoy.toString()), materias)
                                mostrandoAlta = false
                            }
                        )
                    }
                }
                else -> {
                    val esFinDeSemana = fechaReferencia.dayOfWeek.value >= 6
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { fechaRefString = fechaReferencia.minusDays(1).toString() }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Día anterior")
                            }
                            
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showDatePicker = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val fechaTexto = if (fechaReferencia == hoy) {
                                    "Hoxe, ${fechaReferencia.format(DateTimeFormatter.ofPattern("d 'de' MMMM", java.util.Locale("gl")))}"
                                } else {
                                    fechaReferencia.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", java.util.Locale("gl"))).replaceFirstChar { it.uppercase() }
                                }
                                
                                Text(
                                    text = fechaTexto,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                if (fechaReferencia.isBefore(hoy)) {
                                    Text(
                                        " (MODO DIFERIDO)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FunctionalColors.Red,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                            
                            IconButton(onClick = { fechaRefString = fechaReferencia.plusDays(1).toString() }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Día seguinte")
                            }

                            if (!esFinDeSemana) {
                                IconButton(onClick = { mostrandoAlta = true }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Icon(Icons.Default.Add, contentDescription = "Novo Alumno", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!esFinDeSemana) {
                                item {
                                    var localAcomp by remember(aulaId, fechaReferencia) { mutableStateOf(aulaDiaria?.acompanantes ?: "") }
                                    LaunchedEffect(aulaDiaria?.acompanantes) {
                                        if (localAcomp != (aulaDiaria?.acompanantes ?: "")) localAcomp = aulaDiaria?.acompanantes ?: ""
                                    }
                                    
                                    OutlinedTextField(
                                        value = localAcomp,
                                        onValueChange = { 
                                            localAcomp = it
                                            viewModel.guardarAulaDiaria(com.example.cadernodoprofesor.data.AulaDiaria(aulaId, fechaReferencia.toString(), it)) 
                                        },
                                        label = { Text("Acompaña:") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        singleLine = true
                                    )
                                }
                            }

                            if (esFinDeSemana) {
                                item {
                                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                            Spacer(Modifier.height(16.dp))
                                            Text("Fina de semana sen actividade", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Usa o modo diferido para días lectivos", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            } else if (alumnosActivos.isEmpty() && alumnosHistoricos.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Non hai alumnos nesta aula", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                if (alumnosActivos.isNotEmpty()) {
                                    item {
                                        Text("Alumnos Activos", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                                    }
                                    itemsIndexed(alumnosActivos) { index, alumno ->
                                        val tieneAsistencia = asistenciaDia.any { it.alumnoId == alumno.id }
                                        FilaAlumno(
                                            alumno = alumno,
                                            index = index,
                                            esHoy = true,
                                            tieneAsistencia = tieneAsistencia,
                                            onClick = { alumnoIdDetalle = alumno.id },
                                            onAsistencia = { 
                                                if (tieneAsistencia) {
                                                    viewModel.eliminarAsistencia(alumno.id, fechaReferencia)
                                                } else {
                                                    viewModel.registrarAsistencia(alumno.id, fechaReferencia, aulaId)
                                                }
                                            },
                                            aulaId = aulaId
                                        )
                                    }
                                }
                                
                                if (alumnosHistoricos.isNotEmpty()) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { historicoExpandido = !historicoExpandido }
                                                .padding(top = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Histórico de Alumnos", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.weight(1f))
                                            Icon(
                                                if (historicoExpandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = Color.Gray
                                            )
                                        }
                                    }
                                    if (historicoExpandido) {
                                        itemsIndexed(alumnosHistoricos) { index, alumno ->
                                            FilaAlumno(
                                                alumno = alumno,
                                                index = index,
                                                esHoy = false,
                                                tieneAsistencia = false,
                                                onClick = { alumnoIdDetalle = alumno.id },
                                                onAsistencia = {},
                                                aulaId = aulaId
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDate = fechaReferencia,
            onDismiss = { showDatePicker = false },
            onConfirm = { 
                fechaRefString = it.toString()
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleAlumno(
    alumno: Alumno, 
    viewModel: CalendarioViewModel,
    fechaReferencia: LocalDate,
    onBack: () -> Unit,
    onBaja: () -> Unit,
    onUpload: (List<android.net.Uri>) -> Unit,
    onSave: (Alumno) -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Académico", "Facultativo", "Contexto")
    var editandoContexto by rememberSaveable { mutableStateOf(false) }
    var mostrarConfirmacionUpload by rememberSaveable { mutableStateOf(false) }
    var urisPendientes by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    
    val notas by viewModel.obtenerNotasAlumno(alumno.id).collectAsState(initial = emptyList())
    val materias by viewModel.obtenerMateriasAlumno(alumno.id).collectAsState(initial = emptyList())
    val todasLasEntregas by viewModel.obtenerTodasEntregasAlumno(alumno.id).collectAsState(initial = emptyList())
    val movimientos by viewModel.obtenerMovimientosAlumno(alumno.id).collectAsState(initial = emptyList())
    
    val hoy = remember { LocalDate.now() }
    val esHoy = fechaReferencia == hoy
    val esFinDeSemana = fechaReferencia.dayOfWeek.value >= 6

    val registroHoy by viewModel.obtenerRegistroAcademico(alumno.id, fechaReferencia).collectAsState(initial = null)
    val historialAcademico by viewModel.obtenerHistorialAcademico(alumno.id).collectAsState(initial = emptyList())
    
    var registroAnterior by remember { mutableStateOf<RegistroAcademico?>(null) }
    LaunchedEffect(alumno.id, fechaReferencia) {
        registroAnterior = viewModel.obtenerUltimoRegistroAnterior(alumno.id, fechaReferencia)
    }

    var vistaHistorial by remember { mutableStateOf<String?>(null) }
    var materiaSeleccionada by remember { mutableStateOf<MateriaAlumno?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            urisPendientes = uris
            mostrarConfirmacionUpload = true
        }
    }

    // Edición Contexto
    var editNome by remember(alumno, editandoContexto) { mutableStateOf(alumno.nomeCompleto) }
    var editCentro by remember(alumno, editandoContexto) { mutableStateOf(alumno.centroEstudos) }
    var editCurso by remember(alumno, editandoContexto) { mutableStateOf(alumno.curso) }
    var editNivel by remember(alumno, editandoContexto) { mutableStateOf(alumno.nivel) }
    var editRef by remember(alumno, editandoContexto) { mutableStateOf(alumno.contactoReferencia) }
    var editRefNome by remember(alumno, editandoContexto) { mutableStateOf(alumno.contactoNome) }
    var editRefEmail by remember(alumno, editandoContexto) { mutableStateOf(alumno.contactoEmail) }
    var editRefTel by remember(alumno, editandoContexto) { mutableStateOf(alumno.contactoTelefono) }
    var editIngreso by remember(alumno, editandoContexto) { mutableStateOf(alumno.fechaIngreso) }
    var editMedidas by remember(alumno, editandoContexto) { mutableStateOf(alumno.medidasAtencion) }
    var editObxetivos by remember(alumno, editandoContexto) { mutableStateOf(alumno.obxetivosXerais) }

    val rawDifs = alumno.dificultadesAprendizaxe.split("\n")
    var editSeleccionadas by remember(alumno, editandoContexto) { 
        mutableStateOf(rawDifs.filter { it in OPCIONS_DIFICULTADES }) 
    }
    var editOutrasText by remember(alumno, editandoContexto) { 
        mutableStateOf(rawDifs.find { it.startsWith("Outras: ") }?.removePrefix("Outras: ") ?: "") 
    }

    var editandoAcademico by rememberSaveable { mutableStateOf(false) }
    var editEval1 by remember(alumno, editandoAcademico) { mutableStateOf(alumno.fechaEval1) }
    var editEval2 by remember(alumno, editandoAcademico) { mutableStateOf(alumno.fechaEval2) }
    var editEval3 by remember(alumno, editandoAcademico) { mutableStateOf(alumno.fechaEval3) }
    var editMaterias by remember(materias, editandoAcademico) { mutableStateOf(materias) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = alumno.nomeCompleto, style = MaterialTheme.typography.headlineMedium)
        }

        Text(
            text = "${alumno.curso} ${alumno.nivel} - ${alumno.centroEstudos}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontSize = 14.sp) }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> { // Académico
                    AnimatedContent(
                        targetState = Triple(vistaHistorial, materiaSeleccionada, editandoAcademico),
                        transitionSpec = {
                            if (targetState.first != null || targetState.second != null) {
                                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                            } else {
                                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                            }
                        },
                        label = "NavAcademico"
                    ) { (historial, materia, editando) ->
                        if (materia != null) {
                            DetalleMateria(
                                alumno = alumno,
                                materia = materia,
                                viewModel = viewModel,
                                fechaReferencia = fechaReferencia,
                                permitirEntrega = !esFinDeSemana,
                                onBack = { materiaSeleccionada = null }
                            )
                        } else if (historial != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { vistaHistorial = null }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                    }
                                    Text(
                                        text = if (historial == "TRABALLO") "Historial de Traballo" else "Historial de Observacións",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                historialAcademico.forEach { reg ->
                                    val texto = if (historial == "TRABALLO") reg.traballoDia else reg.observacionDia
                                    if (texto.isNotBlank()) {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(reg.fecha, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                Text(texto, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val currentReg = registroHoy ?: RegistroAcademico(alumnoId = alumno.id, fecha = fechaReferencia.toString())
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                InfoSection(title = "Traballo do día") {
                                    var localTraballo by remember(alumno.id, fechaReferencia) { mutableStateOf(currentReg.traballoDia) }
                                    LaunchedEffect(currentReg.traballoDia) {
                                        if (localTraballo != currentReg.traballoDia) localTraballo = currentReg.traballoDia
                                    }
                                    
                                    OutlinedTextField(
                                        value = localTraballo,
                                        onValueChange = { 
                                            localTraballo = it
                                            viewModel.guardarRegistroAcademico(currentReg.copy(traballoDia = it)) 
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Escribe o traballo de hoxe...") },
                                        enabled = !esFinDeSemana,
                                        minLines = 3
                                    )
                                    Text(
                                        "Ver historial de traballo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp).clickable { vistaHistorial = "TRABALLO" }
                                    )
                                }
                                InfoSection(title = "Pendiente da última sesión") {
                                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            val textoPendiente = registroAnterior?.paraProximaSesion ?: "Nada pendente"
                                            Text(textoPendiente, style = MaterialTheme.typography.bodyMedium)
                                            if (registroAnterior?.alertaProximaSesion == true) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                                    Icon(Icons.Default.Warning, null, tint = FunctionalColors.Red, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Alerta activada", color = FunctionalColors.Red, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                                InfoSection(title = "Para a próxima sesión") {
                                    Column {
                                        var localProx by remember(alumno.id, fechaReferencia) { mutableStateOf(currentReg.paraProximaSesion) }
                                        LaunchedEffect(currentReg.paraProximaSesion) {
                                            if (localProx != currentReg.paraProximaSesion) localProx = currentReg.paraProximaSesion
                                        }
                                        
                                        OutlinedTextField(
                                            value = localProx,
                                            onValueChange = { 
                                                localProx = it
                                                viewModel.guardarRegistroAcademico(currentReg.copy(paraProximaSesion = it)) 
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !esFinDeSemana,
                                            placeholder = { Text("Tarefas pendentes para o seguinte día...") }
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = currentReg.alertaProximaSesion, 
                                                onCheckedChange = { 
                                                    viewModel.guardarRegistroAcademico(
                                                        currentReg.copy(
                                                            alertaProximaSesion = it,
                                                            ocultarAlerta = !it // Se activamos alerta, mostramos (false). Se a quitamos, ocultamos (true).
                                                        )
                                                    ) 
                                                }, 
                                                enabled = !esFinDeSemana
                                            )
                                            Text("Alerta", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                InfoSection(title = "Observación do día") {
                                    var localObs by remember(alumno.id, fechaReferencia) { mutableStateOf(currentReg.observacionDia) }
                                    LaunchedEffect(currentReg.observacionDia) {
                                        if (localObs != currentReg.observacionDia) localObs = currentReg.observacionDia
                                    }
                                    
                                    OutlinedTextField(
                                        value = localObs,
                                        onValueChange = { 
                                            localObs = it
                                            viewModel.guardarRegistroAcademico(currentReg.copy(observacionDia = it)) 
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !esFinDeSemana,
                                        placeholder = { Text("Observacións sobre o alumno hoxe...") },
                                        minLines = 2
                                    )
                                    Text("Ver historial de observacións", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp).clickable { vistaHistorial = "OBSERVACION" })
                                }

                                val actionHeaderAcad = @Composable {
                                    Row {
                                        if (editando) {
                                            IconButton(onClick = {
                                                onSave(alumno.copy(fechaEval1 = editEval1, fechaEval2 = editEval2, fechaEval3 = editEval3))
                                                editMaterias.forEach { m -> viewModel.insertarMateriaAlumno(m.copy(alumnoId = alumno.id)) }
                                                editandoAcademico = false
                                            }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Check, contentDescription = "Gardar", modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(onClick = { editandoAcademico = false }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Cancelar", modifier = Modifier.size(20.dp))
                                            }
                                        } else {
                                            IconButton(onClick = { editandoAcademico = true }, modifier = Modifier.size(24.dp), enabled = !esFinDeSemana) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }

                                val proximaEval = remember(alumno) {
                                    val parser = DateTimeFormatter.ofPattern("dd/MM/yy")
                                    val fechas = listOfNotNull(
                                        try { LocalDate.parse(alumno.fechaEval1, parser) } catch (_: Exception) { null },
                                        try { LocalDate.parse(alumno.fechaEval2, parser) } catch (_: Exception) { null },
                                        try { LocalDate.parse(alumno.fechaEval3, parser) } catch (_: Exception) { null }
                                    )
                                    val hoyLocal = LocalDate.now()
                                    fechas.filter { !it.isBefore(hoyLocal) }
                                        .minOrNull()
                                        ?.format(parser) ?: "-"
                                }

                                InfoSection(title = if (editando) "Datas de Avaliación" else "Próxima avaliación", action = actionHeaderAcad) {
                                    if (editando) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(value = editEval1, onValueChange = { editEval1 = it }, label = { Text("1ª") }, modifier = Modifier.weight(1f))
                                            OutlinedTextField(value = editEval2, onValueChange = { editEval2 = it }, label = { Text("2ª") }, modifier = Modifier.weight(1f))
                                            OutlinedTextField(value = editEval3, onValueChange = { editEval3 = it }, label = { Text("3ª") }, modifier = Modifier.weight(1f))
                                        }
                                    } else {
                                        Text(text = proximaEval, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (editando) {
                                    InfoSection(title = "Materias", action = actionHeaderAcad) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            editMaterias.forEachIndexed { index, m ->
                                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            OutlinedTextField(value = m.nombre, onValueChange = { n -> editMaterias = editMaterias.toMutableList().also { it[index] = it[index].copy(nombre = n) } }, label = { Text("Materia") }, modifier = Modifier.weight(1f))
                                                            IconButton(onClick = { if (m.id != 0L) viewModel.eliminarMateriaAlumno(m); editMaterias = editMaterias.toMutableList().also { it.removeAt(index) } }) {
                                                                Icon(Icons.Default.Delete, null, tint = FunctionalColors.Red)
                                                            }
                                                        }
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            OutlinedTextField(value = m.profesor, onValueChange = { p -> editMaterias = editMaterias.toMutableList().also { it[index] = it[index].copy(profesor = p) } }, label = { Text("Profesorado (Opcional)") }, modifier = Modifier.weight(1f))
                                                            OutlinedTextField(value = m.email, onValueChange = { e -> editMaterias = editMaterias.toMutableList().also { it[index] = it[index].copy(email = e) } }, label = { Text("Email (Opcional)") }, modifier = Modifier.weight(1f))
                                                        }
                                                        OutlinedTextField(value = m.objetivos, onValueChange = { o -> editMaterias = editMaterias.toMutableList().also { it[index] = it[index].copy(objetivos = o) } }, label = { Text("Obxectivos a traballar") }, modifier = Modifier.fillMaxWidth())
                                                    }
                                                }
                                            }
                                            TextButton(onClick = { editMaterias = editMaterias + MateriaAlumno(nombre = "", profesor = "", email = "", objetivos = "", alumnoId = alumno.id) }) {
                                                Icon(Icons.Default.Add, null)
                                                Text("Engadir materia nova")
                                            }
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Materias e Entregas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            actionHeaderAcad()
                                        }
                                        if (materias.isEmpty()) {
                                            Text("Sen materias rexistradas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            materias.forEach { m ->
                                                val numEntregas = todasLasEntregas.count { it.materiaId == m.id }
                                                Card(
                                                    modifier = Modifier.fillMaxWidth().clickable { materiaSeleccionada = m },
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(text = m.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                            Text(text = if (numEntregas > 0) "$numEntregas entregas realizadas" else "Sen entregas aínda", style = MaterialTheme.typography.labelSmall, color = if (numEntregas > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> { // Facultativo
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        var razonIngresoText by rememberSaveable { mutableStateOf("") }
                        var alergiasText by rememberSaveable { mutableStateOf("") }
                        var notaDiaText by rememberSaveable { mutableStateOf("") }
                        var notaIdEditando by rememberSaveable { mutableStateOf<Long?>(null) }
                        var contenidoEditando by rememberSaveable { mutableStateOf("") }

                        val ingresoNota = notas.find { it.tipo == "INGRESO" }
                        val alergiaNota = notas.find { it.tipo == "ALERGIA" }
                        val otrasNotas = notas.filter { it.tipo != "INGRESO" && it.tipo != "ALERGIA" }

                        Text("Información Clínica", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (ingresoNota != null) {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FunctionalColors.Purple.copy(alpha = 0.08f))) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Info, null, tint = FunctionalColors.Purple, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Razón de Ingreso", style = MaterialTheme.typography.labelLarge, color = FunctionalColors.Purple, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            if (notaIdEditando == ingresoNota.id) {
                                                IconButton(onClick = { viewModel.actualizarNotaAlumno(ingresoNota.copy(contenido = contenidoEditando)); notaIdEditando = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                                IconButton(onClick = { notaIdEditando = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                            } else {
                                                IconButton(onClick = { notaIdEditando = ingresoNota.id; contenidoEditando = ingresoNota.contenido }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = FunctionalColors.Purple) }
                                                IconButton(onClick = { viewModel.eliminarNotaAlumno(ingresoNota) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = FunctionalColors.Red) }
                                            }
                                        }
                                        if (notaIdEditando == ingresoNota.id) {
                                            OutlinedTextField(value = contenidoEditando, onValueChange = { contenidoEditando = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                        } else {
                                            Text(ingresoNota.contenido, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, start = 26.dp))
                                        }
                                    }
                                }
                            } else {
                                OutlinedTextField(value = razonIngresoText, onValueChange = { razonIngresoText = it }, label = { Text("Definir Razón de ingreso") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { if (razonIngresoText.isNotBlank()) { viewModel.insertarNotaAlumno(alumno.id, "INGRESO", razonIngresoText, fechaReferencia); razonIngresoText = "" } }) { Icon(Icons.AutoMirrored.Filled.Send, null) } })
                            }

                            if (alergiaNota != null) {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FunctionalColors.Red.copy(alpha = 0.08f))) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, null, tint = FunctionalColors.Red, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Alerxias e Advertencias", style = MaterialTheme.typography.labelLarge, color = FunctionalColors.Red, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            if (notaIdEditando == alergiaNota.id) {
                                                IconButton(onClick = { viewModel.actualizarNotaAlumno(alergiaNota.copy(contenido = contenidoEditando)); notaIdEditando = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                                IconButton(onClick = { notaIdEditando = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                            } else {
                                                IconButton(onClick = { notaIdEditando = alergiaNota.id; contenidoEditando = alergiaNota.contenido }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = FunctionalColors.Red) }
                                                IconButton(onClick = { viewModel.eliminarNotaAlumno(alergiaNota) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = FunctionalColors.Red) }
                                            }
                                        }
                                        if (notaIdEditando == alergiaNota.id) {
                                            OutlinedTextField(value = contenidoEditando, onValueChange = { contenidoEditando = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                        } else {
                                            Text(alergiaNota.contenido, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, start = 26.dp), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            } else {
                                OutlinedTextField(value = alergiasText, onValueChange = { alergiasText = it }, label = { Text("Rexistrar Alerxias / Advertencias") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { if (alergiasText.isNotBlank()) { viewModel.insertarNotaAlumno(alumno.id, "ALERGIA", alergiasText, fechaReferencia); alergiasText = "" } }) { Icon(Icons.AutoMirrored.Filled.Send, null) } })
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Anotacións do día", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = notaDiaText, onValueChange = { notaDiaText = it }, label = { Text("Engadir nota diaria...") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { if (notaDiaText.isNotBlank()) { viewModel.insertarNotaAlumno(alumno.id, "DIARIA", notaDiaText, fechaReferencia); notaDiaText = "" } }) { Icon(Icons.AutoMirrored.Filled.Send, null) } })

                        if (otrasNotas.isNotEmpty()) {
                            otrasNotas.forEach { nota ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = nota.fecha, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                            Row {
                                                if (notaIdEditando == nota.id) {
                                                    IconButton(onClick = { viewModel.actualizarNotaAlumno(nota.copy(contenido = contenidoEditando)); notaIdEditando = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                                    IconButton(onClick = { notaIdEditando = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                                                } else {
                                                    IconButton(onClick = { notaIdEditando = nota.id; contenidoEditando = nota.contenido }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
                                                    IconButton(onClick = { viewModel.eliminarNotaAlumno(nota) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = FunctionalColors.Red) }
                                                }
                                            }
                                        }
                                        if (notaIdEditando == nota.id) {
                                            OutlinedTextField(value = contenidoEditando, onValueChange = { contenidoEditando = it }, modifier = Modifier.fillMaxWidth())
                                        } else {
                                            Text(text = nota.contenido, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> { // Contexto
                    val onSaveContexto = {
                        val difs = (editSeleccionadas + (if (editOutrasText.isNotBlank()) listOf("Outras: $editOutrasText") else emptyList())).joinToString("\n")
                        onSave(alumno.copy(nomeCompleto = editNome, centroEstudos = editCentro, curso = editCurso, nivel = editNivel, contactoReferencia = editRef, contactoNome = editRefNome, contactoEmail = editRefEmail, contactoTelefono = editRefTel, contactoRecibeEntregas = alumno.contactoRecibeEntregas, entregaCanalBoxabalar = alumno.entregaCanalBoxabalar, entregaCanalEmail = alumno.entregaCanalEmail, dificultadesAprendizaxe = difs, medidasAtencion = editMedidas, obxetivosXerais = editObxetivos, fechaIngreso = editIngreso))
                        editandoContexto = false
                    }
                    val actionHeader = @Composable {
                        Row {
                            if (editandoContexto) {
                                IconButton(onClick = { onSaveContexto() }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { editandoContexto = false }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp)) }
                            } else {
                                IconButton(onClick = { editandoContexto = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val docsTexto = if (alumno.tieneDocumentacion) "Docs. enviados" else "Envio docs."
                            val docsColor = if (alumno.tieneDocumentacion) FunctionalColors.Green else FunctionalColors.Red
                            TextButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Description, null, tint = docsColor, modifier = Modifier.size(20.dp))
                                Text(docsTexto, color = docsColor, fontSize = 12.sp)
                            }
                            TextButton(onClick = { viewModel.cambiarEstadoAlumno(alumno.id, !alumno.esActivo, fechaReferencia) }, modifier = Modifier.weight(1f)) {
                                Icon(imageVector = if (alumno.esActivo) Icons.AutoMirrored.Filled.Logout else Icons.Default.Login, contentDescription = null, tint = if (alumno.esActivo) FunctionalColors.Red else FunctionalColors.Green, modifier = Modifier.size(20.dp))
                                Text(text = if (alumno.esActivo) "Baixa" else "Alta", color = if (alumno.esActivo) FunctionalColors.Red else FunctionalColors.Green, fontSize = 12.sp)
                            }
                        }

                        InfoSection(title = "Datos Académicos", action = actionHeader) {
                            if (editandoContexto) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(value = editNome, onValueChange = { editNome = it }, label = { Text("Nome completo") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(value = editIngreso, onValueChange = { editIngreso = it }, label = { Text("Fecha de ingreso") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(value = editCentro, onValueChange = { editCentro = it }, label = { Text("Centro") }, modifier = Modifier.fillMaxWidth())
                                }
                            } else { Text(text = "Ingreso: ${alumno.fechaIngreso}\n${alumno.curso} ${alumno.nivel} - ${alumno.centroEstudos}") }
                        }

                        InfoSection(title = "Contacto (${alumno.contactoReferencia})", action = actionHeader) {
                            if (editandoContexto) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(value = editRefNome, onValueChange = { editRefNome = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(value = editRefEmail, onValueChange = { editRefEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(value = editRefTel, onValueChange = { editRefTel = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth())
                                }
                            } else { Text(text = "${alumno.contactoNome}\n${alumno.contactoEmail}\n${alumno.contactoTelefono}") }
                        }

                        InfoSection(title = "Entregas") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Destinatario", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSave(alumno.copy(contactoRecibeEntregas = true)) }) {
                                    RadioButton(selected = alumno.contactoRecibeEntregas, onClick = { onSave(alumno.copy(contactoRecibeEntregas = true)) })
                                    Text("Entregar ó contacto de referencia", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSave(alumno.copy(contactoRecibeEntregas = false)) }) {
                                    RadioButton(selected = !alumno.contactoRecibeEntregas, onClick = { onSave(alumno.copy(contactoRecibeEntregas = false)) })
                                    Text("Entregar a cada profesor individualmente", style = MaterialTheme.typography.bodyMedium)
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("Canles de entrega", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSave(alumno.copy(entregaCanalBoxabalar = !alumno.entregaCanalBoxabalar)) }) {
                                    Checkbox(checked = alumno.entregaCanalBoxabalar, onCheckedChange = { onSave(alumno.copy(entregaCanalBoxabalar = it)) })
                                    Text("Boxabalar (Nextcloud)", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onSave(alumno.copy(entregaCanalEmail = !alumno.entregaCanalEmail)) }) {
                                    Checkbox(checked = alumno.entregaCanalEmail, onCheckedChange = { onSave(alumno.copy(entregaCanalEmail = it)) })
                                    Text("Email", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        InfoSection(title = "Dificultades", action = actionHeader) {
                            if (editandoContexto) { FormularioDificultades(editSeleccionadas, { editSeleccionadas = it }, editOutrasText, { editOutrasText = it }) }
                            else { Text(text = alumno.dificultadesAprendizaxe.ifBlank { "Ningunha" }) }
                        }

                        if (movimientos.isNotEmpty()) {
                            InfoSection(title = "Historial de Altas e Baixas") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    movimientos.forEach { mov ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = if (mov.tipo == "ALTA") "Alta no centro" else "Baixa no centro", color = if (mov.tipo == "ALTA") FunctionalColors.Green else FunctionalColors.Red, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text(text = mov.fecha, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarConfirmacionUpload) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionUpload = false },
            title = { Text("Confirmar envío") },
            text = { Text("Vas enviar ${urisPendientes.size} documentos para ${alumno.nomeCompleto}.") },
            confirmButton = { Button(onClick = { onUpload(urisPendientes); mostrarConfirmacionUpload = false }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { mostrarConfirmacionUpload = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun InfoSection(title: String, action: @Composable (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                action?.invoke()
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioAlumno(
    fechaDefault: LocalDate, 
    alumnosActivos: List<Alumno>,
    alumnosHistoricos: List<Alumno>,
    onReactivar: (Alumno) -> Unit,
    onGuardar: (Alumno, List<MateriaAlumno>) -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var fechaIngreso by remember { mutableStateOf(fechaDefault.toString()) }
    var centro by remember { mutableStateOf("") }
    var curso by remember { mutableStateOf("1º") }
    var nivel by remember { mutableStateOf("Primaria") }
    var ref by remember { mutableStateOf("Titor/a") }
    var refNome by remember { mutableStateOf("") }
    var refEmail by remember { mutableStateOf("") }
    var refTel by remember { mutableStateOf("") }
    var recibeEntregas by remember { mutableStateOf(false) }
    var canalBox by remember { mutableStateOf(true) }
    var canalEmail by remember { mutableStateOf(false) }
    var seleccionadas by remember { mutableStateOf<List<String>>(emptyList()) }
    var outrasText by remember { mutableStateOf("") }
    var med by remember { mutableStateOf("") }
    var obj by remember { mutableStateOf("") }
    var eval1 by remember { mutableStateOf("16/12/26") }
    var eval2 by remember { mutableStateOf("14/03/27") }
    var eval3 by remember { mutableStateOf("05/06/27") }
    var materiasList by remember { mutableStateOf(listOf(
        MateriaAlumno(nombre = "Matemáticas", profesor = "", email = "", objetivos = "", alumnoId = 0),
        MateriaAlumno(nombre = "Lengua Castellana y literatura", profesor = "", email = "", objetivos = "", alumnoId = 0),
        MateriaAlumno(nombre = "Xeografía e Historia", profesor = "", email = "", objetivos = "", alumnoId = 0),
        MateriaAlumno(nombre = "Bioloxía", profesor = "", email = "", objetivos = "", alumnoId = 0),
        MateriaAlumno(nombre = "Física e Química", profesor = "", email = "", objetivos = "", alumnoId = 0),
        MateriaAlumno(nombre = "Tecnoloxía", profesor = "", email = "", objetivos = "", alumnoId = 0),
        MateriaAlumno(nombre = "Educación plastica", profesor = "", email = "", objetivos = "", alumnoId = 0),
        MateriaAlumno(nombre = "Música", profesor = "", email = "", objetivos = "", alumnoId = 0)
    )) }

    val coincidenciaActivo = remember(nome, alumnosActivos) {
        if (nome.isBlank()) null else alumnosActivos.find { it.nomeCompleto.equals(nome, ignoreCase = true) }
    }
    val coincidenciaHistorico = remember(nome, alumnosHistoricos) {
        if (nome.isBlank()) null else alumnosHistoricos.find { it.nomeCompleto.equals(nome, ignoreCase = true) }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome completo") }, modifier = Modifier.fillMaxWidth(), isError = coincidenciaActivo != null || coincidenciaHistorico != null)

            if (coincidenciaActivo != null) { Text(text = "Este alumno xa está activo nesta aula.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
            if (coincidenciaHistorico != null) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FunctionalColors.Purple.copy(alpha = 0.1f))) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Este alumno xa estivo matriculado (Histórico).", color = FunctionalColors.Purple, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onReactivar(coincidenciaHistorico) }, colors = ButtonDefaults.buttonColors(containerColor = FunctionalColors.Purple)) {
                            Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Dar de alta de novo")
                        }
                    }
                }
            }

            OutlinedTextField(value = fechaIngreso, onValueChange = { fechaIngreso = it }, label = { Text("Fecha de ingreso (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = centro, onValueChange = { centro = it }, label = { Text("Centro de Estudos") }, modifier = Modifier.fillMaxWidth())
            
            Text("Preferencias de Entrega", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { recibeEntregas = true }) {
                    RadioButton(selected = recibeEntregas, onClick = { recibeEntregas = true })
                    Text("Entregar ó contacto de referencia", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { recibeEntregas = false }) {
                    RadioButton(selected = !recibeEntregas, onClick = { recibeEntregas = false })
                    Text("Entregar a cada profesor individualmente", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Canles de Entrega", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = canalBox, onCheckedChange = { canalBox = it })
                Text("Boxabalar", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(16.dp))
                Checkbox(checked = canalEmail, onCheckedChange = { canalEmail = it })
                Text("Email", style = MaterialTheme.typography.bodySmall)
            }

            Text("Materias", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            materiasList.forEachIndexed { index, materia ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = materia.nombre, onValueChange = { n -> materiasList = materiasList.toMutableList().also { it[index] = it[index].copy(nombre = n) } }, label = { Text("Materia") }, modifier = Modifier.weight(1f))
                            IconButton(onClick = { materiasList = materiasList.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Default.Delete, null, tint = FunctionalColors.Red) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(value = materia.profesor, onValueChange = { p -> materiasList = materiasList.toMutableList().also { it[index] = it[index].copy(profesor = p) } }, label = { Text("Profesorado (Opcional)") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = materia.email, onValueChange = { e -> materiasList = materiasList.toMutableList().also { it[index] = it[index].copy(email = e) } }, label = { Text("Email (Opcional)") }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            TextButton(onClick = { materiasList = materiasList + MateriaAlumno(nombre = "", profesor = "", email = "", objetivos = "", alumnoId = 0) }) { Icon(Icons.Default.Add, null); Text("Engadir materia nova") }
            Button(onClick = { 
                val difs = (seleccionadas + (if (outrasText.isNotBlank()) listOf("Outras: $outrasText") else emptyList())).joinToString("\n")
                onGuardar(Alumno(nomeCompleto = nome, centroEstudos = centro, curso = curso, nivel = nivel, contactoReferencia = ref, contactoNome = refNome, contactoEmail = refEmail, contactoTelefono = refTel, contactoRecibeEntregas = recibeEntregas, entregaCanalBoxabalar = canalBox, entregaCanalEmail = canalEmail, dificultadesAprendizaxe = difs, medidasAtencion = med, obxetivosXerais = obj, aulaId = "", fechaCreacion = "", fechaIngreso = fechaIngreso, fechaEval1 = eval1, fechaEval2 = eval2, fechaEval3 = eval3), materiasList)
            }, modifier = Modifier.fillMaxWidth(), enabled = nome.isNotBlank() && coincidenciaActivo == null && coincidenciaHistorico == null) { Text("Gardar Alumno") }
        }
    }
}

@Composable
fun DetalleMateria(alumno: Alumno, materia: MateriaAlumno, viewModel: CalendarioViewModel, fechaReferencia: LocalDate, permitirEntrega: Boolean, onBack: () -> Unit) {
    var descripcionEntrega by remember { mutableStateOf("") }
    val entregas by viewModel.obtenerEntregasMateria(alumno.id, materia.id).collectAsState(initial = emptyList())
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.subirTrabajoMateria(alumno, materia, descripcionEntrega, uris, fechaReferencia)
            descripcionEntrega = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(text = materia.nombre, style = MaterialTheme.typography.titleLarge)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nova entrega", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = descripcionEntrega, onValueChange = { descripcionEntrega = it }, label = { Text("Descrición") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { launcher.launch("*/*") }, enabled = descripcionEntrega.isNotBlank() && permitirEntrega, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileUpload, null); Text("Subir ficheiros")
                }
            }
        }

        Text("Entregas realizadas", style = MaterialTheme.typography.titleMedium)
        entregas.forEach { entrega ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entrega.fecha, style = MaterialTheme.typography.labelSmall)
                        if (entrega.canal == "BOX" || entrega.canal == "AMBOS") {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Text("Abrir en Nextcloud", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.clickable { 
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(entrega.urlNextcloud))
                                context.startActivity(intent)
                            })
                        } else { Text("Enviado por Email", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                    }
                    Text(entrega.descripcion, fontWeight = FontWeight.Bold)
                    Text(entrega.archivoNombre, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun FilaAlumno(alumno: Alumno, index: Int, esHoy: Boolean, tieneAsistencia: Boolean, onClick: () -> Unit, onAsistencia: () -> Unit, aulaId: String = "") {
    val containerColor = when {
        !alumno.esActivo -> MaterialTheme.colorScheme.surface
        aulaId == "HDDIJNP" -> Color(0xFFE8F5E9)
        aulaId == "USMIJHAC" -> Color(0xFFFFFDE7)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.alpha(if (alumno.esActivo) 1f else 0.6f),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}.", modifier = Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = alumno.nomeCompleto, fontWeight = FontWeight.Bold)
                Text(text = "${alumno.curso} ${alumno.nivel} - ${alumno.centroEstudos}", style = MaterialTheme.typography.bodySmall)
            }
            if (esHoy && alumno.esActivo) {
                IconButton(onClick = onAsistencia) {
                    Icon(Icons.Default.CheckCircle, null, tint = if (tieneAsistencia) FunctionalColors.Green else Color.LightGray)
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

val OPCIONS_DIFICULTADES = listOf("Falta de hábitos de estudo", "Dificultades na atención", "Lentitude", "Comprensión lectora", "Falta de base", "Falta de interese", "Emocional")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormularioDificultades(seleccionadas: List<String>, onSeleccionChange: (List<String>) -> Unit, outrasText: String, onOutrasTextChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OPCIONS_DIFICULTADES.forEach { opcion ->
                FilterChip(selected = opcion in seleccionadas, onClick = { if (opcion in seleccionadas) onSeleccionChange(seleccionadas - opcion) else onSeleccionChange(seleccionadas + opcion) }, label = { Text(opcion, fontSize = 12.sp) })
            }
        }
        OutlinedTextField(value = outrasText, onValueChange = onOutrasTextChange, label = { Text("Outras") }, modifier = Modifier.fillMaxWidth())
    }
}
