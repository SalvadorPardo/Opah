package com.example.cadernodoprofesor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cadernodoprofesor.data.AppDatabase
import com.example.cadernodoprofesor.ui.calendario.CalendarioScreen
import com.example.cadernodoprofesor.ui.calendario.CalendarioViewModel
import com.example.cadernodoprofesor.ui.alumnos.AlumnosScreen
import com.example.cadernodoprofesor.ui.alumnos.AlumnoFicheiroScreen
import com.example.cadernodoprofesor.ui.informes.InformesScreen
import com.example.cadernodoprofesor.ui.configuracion.ConfiguracionScreen
import com.example.cadernodoprofesor.ui.theme.CadernoDoProfesorTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import com.example.cadernodoprofesor.data.Preferencias
import com.example.cadernodoprofesor.data.AlertaPendiente
import com.example.cadernodoprofesor.ui.theme.FunctionalColors
import com.example.cadernodoprofesor.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            CadernoDoProfesorTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                
                if (showSplash) {
                    SplashScreen(onTimeout = { showSplash = false })
                } else {
                    AppNavegacionDrawer()
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_opah),
                contentDescription = null,
                modifier = Modifier.size(200.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Opah!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Observacións do Profesorado da Aula Hospitalaria",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 1. Representamos de forma elegante las 6 secciones
enum class SeccionApp(val titulo: String, val icono: ImageVector) {
    INICIO("Inicio", Icons.Default.Home),
    HDDIJNP("HDDIJNP", Icons.Default.People),
    USMIJHAC("USMIJHAC", Icons.Default.People),
    INFORMES("Informes", Icons.Default.Assessment),
    CALENDARIO("Calendario", Icons.Default.DateRange),
    ALUMNO_FICHEIRO("Alumno novo con ficheiro", Icons.Default.FileUpload),
    CONFIGURACION("Configuración", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavegacionDrawer() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Configuración de la base de datos y ViewModel
    val database = remember { AppDatabase.getDatabase(context) }
    val calendarioViewModel: CalendarioViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return CalendarioViewModel(database.calendarioDao(), database, context) as T
            }
        }
    )

    // Estado que recuerda qué pantalla está seleccionada actualmente
    var seccionSeleccionada by rememberSaveable { mutableStateOf(SeccionApp.INICIO) }
    var alumnoIdParaAbrir by rememberSaveable { mutableStateOf<Long?>(null) }
    var aulaParaAbrir by rememberSaveable { mutableStateOf<String?>(null) }
    
    val cursoActivo by calendarioViewModel.cursoActivo.collectAsState()
    val prefsNullable by calendarioViewModel.preferencias.collectAsState()
    val prefs = prefsNullable ?: Preferencias()

    // El contenedor del menú lateral desplegable
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Opah!",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { scope.launch { drawerState.close() } },
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Observacións do profesorado da Aula Hospitalaria",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Generamos los botones del menú de forma dinámica
                    SeccionApp.entries.forEach { seccion ->
                        val visible = when (seccion) {
                            SeccionApp.HDDIJNP -> prefs.espazo1Activo
                            SeccionApp.USMIJHAC -> prefs.espazo2Activo
                            else -> true
                        }
                        
                        if (visible) {
                            val titulo = when (seccion) {
                                SeccionApp.HDDIJNP -> prefs.espazo1Acronimo
                                SeccionApp.USMIJHAC -> prefs.espazo2Acronimo
                                else -> seccion.titulo
                            }
                            
                            NavigationDrawerItem(
                                icon = { Icon(seccion.icono, contentDescription = titulo) },
                                label = { Text(titulo) },
                                selected = seccion == seccionSeleccionada,
                                onClick = {
                                    seccionSeleccionada = seccion
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Zona inferior con datos del profesor
                    if (prefs.nombreProfesor.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = prefs.nombreProfesor,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Aula de ${prefs.ciudadAula}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) {
        // El contenido de la pantalla principal
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val tituloApp = when (seccionSeleccionada) {
                            SeccionApp.HDDIJNP -> prefs.espazo1Acronimo
                            SeccionApp.USMIJHAC -> prefs.espazo2Acronimo
                            else -> seccionSeleccionada.titulo
                        }
                        Text(
                            text = tituloApp,
                            modifier = Modifier.clickable {
                                scope.launch { drawerState.open() }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                // Abrimos el menú lateral al pulsar el icono de hamburguesa
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                // Aquí decidimos qué pantalla dibujar basándonos en la sección activa
                when (seccionSeleccionada) {
                    SeccionApp.INICIO -> PantallaInicio(
                        cursoActivoNome = cursoActivo?.nombre ?: "Sen curso activo",
                        onNavigateToCalendario = { seccionSeleccionada = SeccionApp.CALENDARIO },
                        onNavigateToAlumnoFicheiro = { seccionSeleccionada = SeccionApp.ALUMNO_FICHEIRO },
                        onNavigateToAula = { aula ->
                            seccionSeleccionada = if (aula == "HDDIJNP") SeccionApp.HDDIJNP else SeccionApp.USMIJHAC
                        },
                        onNavigateToAlumnoDetail = { aula, id ->
                            alumnoIdParaAbrir = id
                            aulaParaAbrir = aula
                            seccionSeleccionada = if (aula == "HDDIJNP") SeccionApp.HDDIJNP else SeccionApp.USMIJHAC
                        },
                        viewModel = calendarioViewModel
                    )
                    SeccionApp.HDDIJNP -> AlumnosScreen(
                        viewModel = calendarioViewModel, 
                        aulaId = "HDDIJNP",
                        alumnoIdInicial = if (aulaParaAbrir == "HDDIJNP") alumnoIdParaAbrir else null,
                        onDetalleCerrado = { 
                            alumnoIdParaAbrir = null
                            aulaParaAbrir = null
                        }
                    )
                    SeccionApp.USMIJHAC -> AlumnosScreen(
                        viewModel = calendarioViewModel, 
                        aulaId = "USMIJHAC",
                        alumnoIdInicial = if (aulaParaAbrir == "USMIJHAC") alumnoIdParaAbrir else null,
                        onDetalleCerrado = { 
                            alumnoIdParaAbrir = null
                            aulaParaAbrir = null
                        }
                    )
                    SeccionApp.INFORMES -> InformesScreen(calendarioViewModel)
                    SeccionApp.CALENDARIO -> CalendarioScreen(calendarioViewModel)
                    SeccionApp.ALUMNO_FICHEIRO -> AlumnoFicheiroScreen(calendarioViewModel)
                    SeccionApp.CONFIGURACION -> ConfiguracionScreen(calendarioViewModel)
                }
            }
        }
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_5)
@Composable
fun PreviewAppPixel5() {
    CadernoDoProfesorTheme {
        AppNavegacionDrawer()
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_5)
@Composable
fun PreviewDrawerAbierto() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    CadernoDoProfesorTheme {
        // Para la previa forzamos el estado abierto
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Opah!",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    SeccionApp.entries.forEach { seccion ->
                        NavigationDrawerItem(
                            icon = { Icon(seccion.icono, contentDescription = seccion.titulo) },
                            label = { Text(seccion.titulo) },
                            selected = seccion == SeccionApp.INICIO,
                            onClick = {},
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                }
            }
        ) {
            Scaffold { padding -> Box(Modifier.padding(padding)) }
        }
    }
}

// Una pantalla genérica temporal para el prototipo
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicio(
    cursoActivoNome: String, 
    onNavigateToCalendario: () -> Unit, 
    onNavigateToAlumnoFicheiro: () -> Unit,
    onNavigateToAula: (String) -> Unit,
    onNavigateToAlumnoDetail: (String, Long) -> Unit,
    viewModel: CalendarioViewModel
) {
    var fechaSeleccionadaStr by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val fechaSeleccionada = remember(fechaSeleccionadaStr) { LocalDate.parse(fechaSeleccionadaStr) }
    var mostrarDatePicker by remember { mutableStateOf(false) }

    // Formateador en Galego
    val localeGalego = remember { Locale.forLanguageTag("gl-ES") }
    val formatter = remember(localeGalego) { DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", localeGalego) }
    
    // Función para capitalizar la primera letra (opcional, para que quede más elegante)
    fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(localeGalego) else it.toString() }

    // Función para formatear la fecha con el mes en mayúsculas
    fun LocalDate.formatEnGalego(): String {
        val diaSemana = format(DateTimeFormatter.ofPattern("EEEE", localeGalego)).capitalize()
        val mes = format(DateTimeFormatter.ofPattern("MMMM", localeGalego)).capitalize()
        return "$diaSemana, $dayOfMonth de $mes de $year"
    }

    val hoxe = remember { LocalDate.now() }
    val esHoxe = fechaSeleccionada == hoxe
    val esPasado = fechaSeleccionada.isBefore(hoxe)

    val alertas by viewModel.obtenerAlertasPendientes().collectAsState(initial = emptyList())
    val resumenAlumnos by viewModel.obtenerResumenAlumnos().collectAsState(initial = emptyMap())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp)
    ) {
        // Resumen de Alumnos por Aula
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf("HDDIJNP", "USMIJHAC").forEach { aula ->
                val datos = resumenAlumnos[aula] ?: Pair(0, 0)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToAula(aula) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (aula == "HDDIJNP") Color(0xFFE8F5E9) else Color(0xFFFFFDE7)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = aula, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(text = "${datos.first}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = " activos", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text(text = "${datos.second} acumulados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Text(
            text = when {
                esHoxe -> "Hoxe é"
                esPasado -> "Día pasado"
                else -> "Aínda por chegar"
            },
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (esHoxe) FontWeight.Medium else FontWeight.Bold
            ),
            color = when {
                esHoxe -> MaterialTheme.colorScheme.secondary
                esPasado -> Color.Red
                else -> Color(0xFF2E7D32) // Verde escuro
            }
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { fechaSeleccionadaStr = fechaSeleccionada.minusDays(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Día anterior")
            }

            TextButton(
                onClick = { mostrarDatePicker = true },
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = fechaSeleccionada.formatEnGalego(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            IconButton(onClick = { fechaSeleccionadaStr = fechaSeleccionada.plusDays(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Día seguinte")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = cursoActivoNome,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onNavigateToCalendario() }
            )

            if (!esHoxe) {
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { fechaSeleccionadaStr = hoxe.toString() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Text("Ver Hoxe")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.realizarCopiaSeguridade() },
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copia de seguridade", textAlign = TextAlign.Center, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel.limpiarBaseDatosAlumnos() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("Limpiar Base de Datos de Alumnos")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateToAlumnoFicheiro,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Alumno novo con ficheiro (ODS)")
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Tarefas pendentes (Alertas)",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        
        if (alertas.isEmpty()) {
            Text(
                text = "Non hai tarefas pendentes con alerta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                alertas.forEach { alerta ->
                    val colorFondo = if (!alerta.estaActiva) {
                        Color(0xFFEEEEEE) // Gris clarito para históricas
                    } else if (alerta.alumnoAula == "HDDIJNP") {
                        Color(0xFFE8F5E9) // Verde clarito
                    } else {
                        Color(0xFFFFFDE7) // Amarelo clarito
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAlumnoDetail(alerta.alumnoAula, alerta.alumnoId) },
                        colors = CardDefaults.cardColors(
                            containerColor = colorFondo
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val primerNombre = alerta.alumnoNombre.split(" ").firstOrNull() ?: ""
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$primerNombre [${alerta.alumnoAula}]",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (alerta.estaActiva) FunctionalColors.Red else Color.Gray
                                    )
                                    Text(
                                        text = alerta.fecha,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!alerta.estaActiva) {
                                        IconButton(
                                            onClick = { viewModel.ocultarAlerta(alerta.alumnoId, alerta.fecha) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete, 
                                                contentDescription = "Borrar",
                                                modifier = Modifier.size(18.dp),
                                                tint = Color.Gray
                                            )
                                        }
                                    }
                                    
                                    Switch(
                                        checked = alerta.estaActiva,
                                        onCheckedChange = { viewModel.actualizarEstadoAlerta(alerta.alumnoId, alerta.fecha, it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = FunctionalColors.Red,
                                            checkedTrackColor = FunctionalColors.Red.copy(alpha = 0.5f),
                                            uncheckedThumbColor = Color.Gray,
                                            uncheckedTrackColor = Color.LightGray
                                        ),
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = alerta.paraProximaSesion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (alerta.estaActiva) Color.Unspecified else Color.Gray
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Versión ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    if (mostrarDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = fechaSeleccionada.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        
        // Formateador personalizado para el DatePicker en galego con mayúsculas
        val dateFormatter = remember {
            object : DatePickerFormatter {
                override fun formatDate(
                    dateMillis: Long?,
                    locale: CalendarLocale,
                    forContentDescription: Boolean
                ): String? {
                    if (dateMillis == null) return null
                    val date = java.time.Instant.ofEpochMilli(dateMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    // Usamos el formatter de la clase que ya tiene gl-ES y capitalize
                    return date.format(formatter).capitalize()
                }

                override fun formatMonthYear(
                    monthMillis: Long?,
                    locale: CalendarLocale
                ): String? {
                    if (monthMillis == null) return null
                    val date = java.time.Instant.ofEpochMilli(monthMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    val mYFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", localeGalego)
                    return date.format(mYFormatter).capitalize()
                }
            }
        }
        
        DatePickerDialog(
            onDismissRequest = { mostrarDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        fechaSeleccionadaStr = java.time.Instant.ofEpochMilli(it)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate().toString()
                    }
                    mostrarDatePicker = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                dateFormatter = dateFormatter
            )
        }
    }
}

@Composable
fun PantallaInformativa(mensaje: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        Text(
            text = mensaje,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Aquí diseñaremos el contenido de esta sección.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}