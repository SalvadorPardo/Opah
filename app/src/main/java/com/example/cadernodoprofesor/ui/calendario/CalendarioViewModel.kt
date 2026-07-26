package com.example.cadernodoprofesor.ui.calendario

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.cadernodoprofesor.R
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cadernodoprofesor.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileOutputStream
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import javax.mail.*
import javax.mail.internet.*
import javax.activation.DataHandler
import javax.mail.util.ByteArrayDataSource

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarioViewModel(
    private val repository: CalendarioDao,
    private val database: AppDatabase,
    private val context: Context
) : ViewModel() {

    val cursos = repository.obtenerTodosLosCursos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cursoActivo = repository.obtenerCursoActivo().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val preferencias = repository.obtenerPreferencias().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Preferencias())

    val festivos = cursoActivo.flatMapLatest { curso ->
        if (curso != null) repository.obtenerCalendarioPorCurso(curso.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val eventos = cursoActivo.flatMapLatest { curso ->
        if (curso != null) repository.obtenerEventosPorCurso(curso.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            cursoActivo.collect { curso ->
                curso?.let { verificarFinDeCurso(it) }
            }
        }
        limpiarAlumnosDuplicados()
    }

    private suspend fun verificarFinDeCurso(curso: Curso) {
        try {
            val hoy = LocalDate.now()
            val fechaFin = LocalDate.parse(curso.fechaFin)
            
            if (hoy.isAfter(fechaFin)) {
                val alumnosActivos = repository.obtenerAlumnosPorAula("HDDIJNP", true).first() + 
                                     repository.obtenerAlumnosPorAula("USMIJHAC", true).first()
                
                if (alumnosActivos.isNotEmpty()) {
                    alumnosActivos.forEach { alumno ->
                        cambiarEstadoAlumno(alumno.id, false)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Curso rematado. Alumnos pasados ao histórico.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FinCurso", "Erro ao verificar fin de curso", e)
        }
    }

    fun establecerCursoActivo(cursoId: Long) {
        viewModelScope.launch { repository.establecerCursoActivo(cursoId) }
    }

    fun insertarCurso(nombre: String, inicio: LocalDate, fin: LocalDate) {
        viewModelScope.launch {
            val curso = Curso(nombre = nombre, fechaInicio = inicio.toString(), fechaFin = fin.toString(), estaActivo = false)
            val id = repository.insertarCurso(curso)
            if (cursoActivo.value == null) {
                establecerCursoActivo(id)
            }
        }
    }

    fun insertarFestivo(nombre: String, inicio: LocalDate, fin: LocalDate?, esUnDia: Boolean, cursoId: Long) {
        viewModelScope.launch {
            if (esUnDia) {
                repository.insertarDia(DiaCalendario(inicio.toString(), cursoId, false, nombre))
            } else if (fin != null) {
                var current = inicio
                val dias = mutableListOf<DiaCalendario>()
                while (!current.isAfter(fin)) {
                    dias.add(DiaCalendario(current.toString(), cursoId, false, nombre))
                    current = current.plusDays(1)
                }
                repository.insertarDias(dias)
            }
        }
    }

    fun insertarEvento(tipo: String, descripcion: String, inicio: LocalDate, fin: LocalDate?, aulaId: String) {
        viewModelScope.launch {
            cursoActivo.value?.let { curso ->
                if (fin == null || fin == inicio) {
                    repository.insertarEvento(Evento(fecha = inicio.toString(), tipoEvento = tipo, aulaId = aulaId, descripcion = descripcion, cursoId = curso.id))
                } else {
                    var current = inicio
                    val eventosNuevos = mutableListOf<Evento>()
                    while (!current.isAfter(fin)) {
                        eventosNuevos.add(Evento(fecha = current.toString(), tipoEvento = tipo, aulaId = aulaId, descripcion = descripcion, cursoId = curso.id))
                        current = current.plusDays(1)
                    }
                    eventosNuevos.forEach { repository.insertarEvento(it) }
                }
            }
        }
    }

    fun eliminarFestivosPorNombre(nombre: String, cursoId: Long) {
        viewModelScope.launch {
            val list = festivos.value.filter { it.nombreFestivo == nombre && it.cursoId == cursoId }
            list.forEach { repository.eliminarDia(it) }
        }
    }

    fun eliminarEvento(evento: Evento) {
        viewModelScope.launch { repository.eliminarEvento(evento) }
    }

    fun eliminarCurso(curso: Curso) {
        viewModelScope.launch { repository.eliminarCurso(curso) }
    }

    fun limpiarBaseDatosAlumnos() {
        viewModelScope.launch {
            repository.eliminarTodosLosAlumnos()
            repository.eliminarTodasLasMaterias()
            repository.eliminarTodasLasNotas()
            repository.eliminarTodaLaAsistencia()
            repository.eliminarTodosLosRegistrosAcademicos()
            repository.eliminarTodosLosMovimientos()
            withContext(Dispatchers.Main) { Toast.makeText(context, "Base de datos limpada", Toast.LENGTH_SHORT).show() }
        }
    }

    fun limpiarAlumnosDuplicados() {
        viewModelScope.launch {
            val todosLosAlumnos = repository.obtenerTodosAlumnosPorAula("AMBAS").first()
            val duplicados = todosLosAlumnos.groupBy { it.aulaId to it.nomeCompleto.lowercase().trim() }
                .filter { it.value.size > 1 }

            if (duplicados.isNotEmpty()) {
                var totalBorrados = 0
                duplicados.forEach { (_, alumnos) ->
                    // Mantemos o que teña o ID máis baixo (o máis antigo probablemente)
                    val aMantenir = alumnos.minBy { it.id }
                    val aBorrar = alumnos.filter { it.id != aMantenir.id }
                    
                    aBorrar.forEach { alumno ->
                        repository.eliminarAlumno(alumno)
                        totalBorrados++
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Borrados $totalBorrados alumnos duplicados", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun obtenerAlumnos(aulaId: String, soloActivos: Boolean): Flow<List<Alumno>> = repository.obtenerAlumnosPorAula(aulaId, soloActivos)

    fun obtenerResumenAlumnos(): Flow<Map<String, Pair<Int, Int>>> {
        return repository.obtenerTodosAlumnosPorAula("AMBAS").map { todos ->
            val resumen = mutableMapOf<String, Pair<Int, Int>>()
            listOf("HDDIJNP", "USMIJHAC").forEach { aula ->
                val alumnosAula = todos.filter { it.aulaId == aula }
                val activos = alumnosAula.count { it.esActivo }
                val acumulados = alumnosAula.size
                resumen[aula] = Pair(activos, acumulados)
            }
            resumen
        }
    }

    fun obtenerAlumnosConEstadoEnFecha(aulaId: String, fecha: LocalDate): Flow<Pair<List<Alumno>, List<Alumno>>> {
        return combine(
            repository.obtenerTodosAlumnosPorAula(aulaId),
            repository.obtenerMovimientosPorAula(aulaId)
        ) { alumnos, movimientos ->
            val fechaStr = fecha.toString()
            val activos = mutableListOf<Alumno>()
            val historicos = mutableListOf<Alumno>()
            
            alumnos.forEach { alumno ->
                val movsAlumno = movimientos.filter { it.alumnoId == alumno.id }
                
                // Determinamos a data de aparición (inscrición)
                val primeraAlta = movsAlumno.filter { it.tipo == "ALTA" }.minByOrNull { it.fecha }
                val fechaInscripcionStr = primeraAlta?.fecha ?: alumno.fechaIngreso.ifBlank { alumno.fechaCreacion }
                
                if (fechaInscripcionStr.isNotBlank()) {
                    val fInscripcion = try { LocalDate.parse(fechaInscripcionStr) } catch(e: Exception) { null }
                    
                    if (fInscripcion != null && !fecha.isBefore(fInscripcion)) {
                        // O alumno xa "existe" nesta data
                        val ultimoMovAFecha = movsAlumno
                            .filter { it.fecha <= fechaStr }
                            .maxByOrNull { it.fecha }
                        
                        // Se o último movemento foi ALTA (ou é o inicial implícito), está activo
                        if (ultimoMovAFecha == null || ultimoMovAFecha.tipo == "ALTA") {
                            activos.add(alumno.copy(esActivo = true))
                        } else {
                            // Se o último movemento foi BAIXA, está no histórico
                            historicos.add(alumno.copy(esActivo = false))
                        }
                    }
                }
            }
            Pair(activos, historicos)
        }
    }

    fun insertarAlumnoConMaterias(alumno: Alumno, materias: List<MateriaAlumno>) {
        viewModelScope.launch {
            val id = repository.insertarAlumno(alumno)
            materias.forEach { if (it.nombre.isNotBlank()) repository.insertarMateriaAlumno(it.copy(alumnoId = id)) }
            
            // Rexistramos o movemento inicial de ALTA na data de ingreso (ou hoxe por defecto)
            val fechaMov = if (alumno.fechaIngreso.isNotBlank()) alumno.fechaIngreso else LocalDate.now().toString()
            repository.insertarMovimientoAlumno(MovimientoAlumno(alumnoId = id, tipo = "ALTA", fecha = fechaMov))
        }
    }

    fun cambiarEstadoAlumno(alumnoId: Long, esActivo: Boolean, fecha: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            repository.actualizarEstadoAlumno(alumnoId, esActivo)
            repository.insertarMovimientoAlumno(MovimientoAlumno(alumnoId = alumnoId, tipo = if (esActivo) "ALTA" else "BAIXA", fecha = fecha.toString()))
        }
    }

    fun registrarAsistencia(alumnoId: Long, fecha: LocalDate, aulaId: String) {
        viewModelScope.launch { repository.registrarAsistencia(Asistencia(alumnoId, fecha.toString(), aulaId)) }
    }

    fun eliminarAsistencia(alumnoId: Long, fecha: LocalDate) {
        viewModelScope.launch { repository.eliminarAsistencia(alumnoId, fecha.toString()) }
    }

    fun obtenerAsistencia(fecha: LocalDate, aulaId: String): Flow<List<Asistencia>> = repository.obtenerAsistenciaDelDia(fecha.toString(), aulaId)

    fun obtenerAsistenciaRango(inicio: LocalDate, fin: LocalDate, aulaId: String): Flow<List<Asistencia>> = repository.obtenerAsistenciaRango(inicio.toString(), fin.toString(), aulaId)
    fun obtenerEventosRango(inicio: LocalDate, fin: LocalDate, aulaId: String): Flow<List<Evento>> = repository.obtenerEventosRango(inicio.toString(), fin.toString(), aulaId)

    fun obtenerNotasAlumno(alumnoId: Long): Flow<List<NotaAlumno>> = repository.obtenerNotasAlumno(alumnoId)

    fun insertarNotaAlumno(alumnoId: Long, tipo: String, contenido: String, fecha: LocalDate = LocalDate.now()) {
        viewModelScope.launch { repository.insertarNotaAlumno(NotaAlumno(alumnoId = alumnoId, fecha = fecha.toString(), tipo = tipo, contenido = contenido)) }
    }

    fun actualizarNotaAlumno(nota: NotaAlumno) { viewModelScope.launch { repository.insertarNotaAlumno(nota) } }

    fun eliminarNotaAlumno(nota: NotaAlumno) { viewModelScope.launch { repository.eliminarNotaAlumno(nota) } }

    fun obtenerMateriasAlumno(alumnoId: Long): Flow<List<MateriaAlumno>> = repository.obtenerMateriasAlumno(alumnoId)

    fun insertarMateriaAlumno(materia: MateriaAlumno) { viewModelScope.launch { repository.insertarMateriaAlumno(materia) } }

    fun eliminarMateriaAlumno(materia: MateriaAlumno) { viewModelScope.launch { repository.eliminarMateriaAlumno(materia) } }

    fun obtenerEntregasMateria(alumnoId: Long, materiaId: Long): Flow<List<EntregaTrabajo>> = repository.obtenerEntregasPorMateria(alumnoId, materiaId)

    fun obtenerTodasEntregasAlumno(alumnoId: Long): Flow<List<EntregaTrabajo>> = repository.obtenerTodasEntregasAlumno(alumnoId)

    fun insertarAlumno(alumno: Alumno) { viewModelScope.launch { repository.insertarAlumno(alumno) } }

    fun obtenerMovimientosAlumno(alumnoId: Long): Flow<List<MovimientoAlumno>> = repository.obtenerMovimientosAlumno(alumnoId)

    private fun getWebDavUrl(prefs: Preferencias): String {
        val user = try { URLEncoder.encode(prefs.abalarboxUsuario.trim(), "UTF-8").replace("+", "%20") } catch(ex: Exception) { prefs.abalarboxUsuario.trim() }
        val servidor = prefs.abalarboxServidor.trim()
        val httpUrl = servidor.toHttpUrlOrNull()
        val host = httpUrl?.host?.trim()?.lowercase() ?: "boxabalar.edu.xunta.gal"
        return "https://$host/remote.php/dav/files/$user/"
    }

    private fun getOcsBaseUrl(prefs: Preferencias): String {
        val servidor = prefs.abalarboxServidor.trim()
        val httpUrl = servidor.toHttpUrlOrNull()
        val host = httpUrl?.host?.trim()?.lowercase() ?: "boxabalar.edu.xunta.gal"
        return "https://$host/ocs/v2.php/apps/files_sharing/api/v1/shares"
    }

    private fun encode(path: String): String = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    fun realizarCopiaSeguridade() {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
            withContext(Dispatchers.IO) {
                try {
                    // Aseguramos que todos os datos pendentes se garden no ficheiro principal
                    try {
                        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
                    } catch (e: Exception) {
                        Log.e("Backup", "Erro no checkpoint: ${e.message}")
                    }

                    val dbFile = context.getDatabasePath("calendario_profesor_db")
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    val fileName = "copia_$timestamp.db"
                    // Forzamos HTTP/1.1 para máxima compatibilidad con servidores de la Xunta
                    val client = OkHttpClient.Builder()
                        .protocols(listOf(Protocol.HTTP_1_1))
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val auth = Credentials.basic(prefs.abalarboxUsuario.trim(), prefs.abalarboxClave.trim())
                    val webDavBase = getWebDavUrl(prefs)
                    
                    listOf("Opah!", "Opah!/Basededatos").forEach { dir ->
                        client.newCall(Request.Builder().url(webDavBase + encode(dir)).header("Authorization", auth).method("MKCOL", null).build()).execute().close()
                    }

                    client.newCall(Request.Builder()
                        .url(webDavBase + encode("Opah!/Basededatos/$fileName"))
                        .header("Authorization", auth)
                        .put(dbFile.asRequestBody("application/x-sqlite3".toMediaTypeOrNull()))
                        .build()).execute().use { response ->
                        withContext(Dispatchers.Main) { Toast.makeText(context, if (response.isSuccessful) "Copia OK" else "Error copia: ${response.code}", Toast.LENGTH_SHORT).show() }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error DNS/Red: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    fun subirDocumentacionAlumno(alumno: Alumno, uris: List<Uri>) {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
            val auth = Credentials.basic(prefs.abalarboxUsuario.trim(), prefs.abalarboxClave.trim())
            val webDavBase = getWebDavUrl(prefs)
            val ocsUrl = getOcsBaseUrl(prefs)
            val alumnoCarpeta = alumno.nomeCompleto.replace(" ", "_")
            val relativePath = "Opah!/${alumno.aulaId}/$alumnoCarpeta/Documentacion"
            
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .protocols(listOf(Protocol.HTTP_1_1))
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    listOf("Opah!", "Opah!/${alumno.aulaId}", "Opah!/${alumno.aulaId}/$alumnoCarpeta", relativePath).forEach { dir ->
                        client.newCall(Request.Builder().url(webDavBase + encode(dir)).header("Authorization", auth).method("MKCOL", null).build()).execute().close()
                    }
                    var count = 0
                    uris.forEachIndexed { index, uri ->
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val fileName = "doc_${System.currentTimeMillis()}_${index + 1}.jpg"
                            client.newCall(Request.Builder().url(webDavBase + encode("$relativePath/$fileName")).header("Authorization", auth).put(input.readBytes().toRequestBody("image/jpeg".toMediaTypeOrNull())).build()).execute().use { if (it.isSuccessful) count++ }
                        }
                    }
                    if (count > 0 && alumno.contactoEmail.isNotBlank()) {
                        client.newCall(Request.Builder().url(ocsUrl).header("Authorization", auth).header("OCS-APIRequest", "true").post(FormBody.Builder().add("path", "/$relativePath").add("shareType", "4").add("shareWith", alumno.contactoEmail).add("permissions", "1").build()).build()).execute().close()
                    }
                    withContext(Dispatchers.Main) { 
                        if (count > 0) {
                            repository.insertarAlumno(alumno.copy(tieneDocumentacion = true))
                            Toast.makeText(context, "Documentación subida", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error en subida: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    fun subirTrabajoMateria(alumno: Alumno, materia: MateriaAlumno, descripcion: String, uris: List<Uri>, fecha: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
            val auth = Credentials.basic(prefs.abalarboxUsuario.trim(), prefs.abalarboxClave.trim())
            val webDavBase = getWebDavUrl(prefs)
            val ocsUrl = getOcsBaseUrl(prefs)
            val alumnoCarpeta = alumno.nomeCompleto.replace(" ", "_")
            val materiaCarpeta = materia.nombre.replace(" ", "_")
            val relativePath = "Opah!/${alumno.aulaId}/$alumnoCarpeta/$materiaCarpeta"
            
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .protocols(listOf(Protocol.HTTP_1_1))
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    listOf("Opah!", "Opah!/${alumno.aulaId}", "Opah!/${alumno.aulaId}/$alumnoCarpeta", relativePath).forEach { dir ->
                        client.newCall(Request.Builder().url(webDavBase + encode(dir)).header("Authorization", auth).method("MKCOL", null).build()).execute().close()
                    }
                    var count = 0
                    uris.forEachIndexed { index, uri ->
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val formatter = DateTimeFormatter.ofPattern("ddMMyyyy_HHmm")
                            val fechaHora = LocalDateTime.now().format(formatter)
                            val materiaLimpia = materia.nombre.replace(" ", "_")
                            val extension = context.contentResolver.getType(uri)?.substringAfterLast("/") ?: "bin"
                            
                            // Engadimos un índice se hai varios ficheiros para evitar duplicados no mesmo minuto
                            val suffix = if (uris.size > 1) "_${index + 1}" else ""
                            val fileName = "Traballo_${materiaLimpia}_${fechaHora}${suffix}.${extension}"

                            val uploadUrl = webDavBase + encode("$relativePath/$fileName")
                            client.newCall(Request.Builder().url(uploadUrl).header("Authorization", auth).put(input.readBytes().toRequestBody("application/octet-stream".toMediaTypeOrNull())).build()).execute().use { response ->
                                if (response.isSuccessful) {
                                    count++
                                    val canalStr = when {
                                        alumno.entregaCanalBoxabalar && alumno.entregaCanalEmail -> "AMBOS"
                                        alumno.entregaCanalEmail -> "EMAIL"
                                        else -> "BOX"
                                    }
                                    repository.insertarEntregaTrabajo(EntregaTrabajo(alumnoId = alumno.id, materiaId = materia.id, fecha = fecha.toString(), descripcion = descripcion, archivoNombre = fileName, urlNextcloud = uploadUrl, canal = canalStr))
                                }
                            }
                        }
                    }
                    if (count > 0) {
                        // Canal Boxabalar
                        if (alumno.entregaCanalBoxabalar) {
                            if (alumno.contactoRecibeEntregas && alumno.contactoEmail.isNotBlank()) {
                                // Enviar SOLO ao contacto de referencia con permisos totais (31)
                                client.newCall(Request.Builder().url(ocsUrl).header("Authorization", auth).header("OCS-APIRequest", "true").post(FormBody.Builder().add("path", "/$relativePath").add("shareType", "4").add("shareWith", alumno.contactoEmail).add("permissions", "31").build()).build()).execute().close()
                            } else if (!alumno.contactoRecibeEntregas && materia.email.isNotBlank()) {
                                // Comportamento estándar: enviar ao profesor da materia
                                client.newCall(Request.Builder().url(ocsUrl).header("Authorization", auth).header("OCS-APIRequest", "true").post(FormBody.Builder().add("path", "/$relativePath").add("shareType", "4").add("shareWith", materia.email).add("permissions", "1").build()).build()).execute().close()
                            }
                        }

                        // Canal Email
                        if (alumno.entregaCanalEmail && prefs.emailClave.isNotBlank() && alumno.contactoEmail.isNotBlank()) {
                            enviarEmailConTraballo(alumno, materia, descripcion, uris, prefs)
                        }
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Traballo entregado", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    private suspend fun enviarEmailConTraballo(alumno: Alumno, materia: MateriaAlumno, descripcion: String, uris: List<Uri>, prefs: Preferencias) {
        withContext(Dispatchers.IO) {
            try {
                val props = Properties()
                props["mail.smtp.auth"] = "true"
                props["mail.smtp.starttls.enable"] = "true"
                props["mail.smtp.host"] = prefs.emailSmtpServidor
                props["mail.smtp.port"] = prefs.emailSmtpPuerto.toString()
                props["mail.smtp.ssl.protocols"] = "TLSv1.2"
                props["mail.debug"] = "true"
                
                // Usamos a dirección completa tanto para o login como para o remitente
                val remitente = if (prefs.emailDireccion.isNotBlank()) prefs.emailDireccion else "${prefs.abalarboxUsuario}@edu.xunta.gal"
                val loginUser = if (prefs.emailDireccion.isNotBlank()) prefs.emailDireccion else prefs.abalarboxUsuario

                val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(loginUser, prefs.emailClave)
                    }
                })

                val message = MimeMessage(session)
                message.setFrom(InternetAddress(remitente))
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(alumno.contactoEmail))
                message.subject = "Entrega Aula Hospitalaria [${materia.nombre}]"

                val messageBodyPart = MimeBodyPart()
                messageBodyPart.setText(descripcion)

                val multipart = MimeMultipart()
                multipart.addBodyPart(messageBodyPart)

                uris.forEach { uri ->
                    val attachPart = MimeBodyPart()
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: byteArrayOf()
                    val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val dataSource = ByteArrayDataSource(bytes, type)
                    attachPart.dataHandler = DataHandler(dataSource)
                    
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        cursor.getString(nameIndex)
                    } ?: "archivo"
                    
                    attachPart.fileName = fileName
                    multipart.addBodyPart(attachPart)
                }

                message.setContent(multipart)
                Transport.send(message)
                Log.d("Email", "Email enviado correctamente a ${alumno.contactoEmail}")
            } catch (e: Exception) {
                Log.e("Email", "Erro enviando email", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro no envío de email: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun generarInformeAlta(
        alumno: Alumno,
        objetivosRatings: Map<String, String>,
        competenciasRatings: Map<String, String>,
        fechaInicio: LocalDate,
        fechaFin: LocalDate,
        incluirTrabajo: Boolean = true,
        incluirEntregas: Boolean = true,
        incluirObjetivos: Boolean = true,
        incluirCompetencias: Boolean = true,
        comentarios: String = ""
    ) {
        viewModelScope.launch {
            val notas = repository.obtenerNotasAlumno(alumno.id).first()
            val asistencias = repository.obtenerAsistenciaRango(fechaInicio.toString(), fechaFin.toString(), alumno.aulaId).first()
            val asistenciaAlumno = asistencias.filter { it.alumnoId == alumno.id }
            val historial = repository.obtenerHistorialAcademico(alumno.id).first()
            val registrosPeriodo = historial.filter {
                try {
                    val f = LocalDate.parse(it.fecha)
                    !f.isBefore(fechaInicio) && !f.isAfter(fechaFin)
                } catch(_: Exception) { false }
            }.sortedBy { it.fecha }
            
            val entregas = repository.obtenerTodasEntregasAlumno(alumno.id).first().filter {
                try {
                    val f = LocalDate.parse(it.fecha)
                    !f.isBefore(fechaInicio) && !f.isAfter(fechaFin)
                } catch(_: Exception) { false }
            }.sortedBy { it.fecha }
            
            val materias = repository.obtenerMateriasAlumno(alumno.id).first()

            val prefs = preferencias.value ?: Preferencias()
            val alumnoCarpeta = alumno.nomeCompleto.replace(" ", "_")
            val relativePath = "Opah!/INFORMES/${alumno.aulaId}"
            val fileName = "Informe_individual_${alumnoCarpeta}.pdf"
            
            withContext(Dispatchers.IO) {
                try {
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    var pageNumber = 1
                    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    var page = pdfDocument.startPage(pageInfo)
                    var canvas = page.canvas
                    val paint = android.graphics.Paint()
                    val textPaint = android.text.TextPaint(paint)
                    var y = 60f
                    
                    // Logo Header
                    try {
                        val resId = context.resources.getIdentifier("logo_informe", "drawable", context.packageName)
                        if (resId != 0) {
                            val logo = BitmapFactory.decodeResource(context.resources, resId)
                            if (logo != null) {
                                val aspectRatio = logo.height.toFloat() / logo.width.toFloat()
                                val logoWidth = 515f
                                val logoHeight = logoWidth * aspectRatio
                                canvas.drawBitmap(logo, null, android.graphics.RectF(40f, 20f, 40f + logoWidth, 20f + logoHeight), paint)
                                y = 20f + logoHeight + 20f
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PDF", "Erro ao cargar o logo", e)
                    }

                    fun checkNewPage(requiredSpace: Float) {
                        if (y + requiredSpace > 780f) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            y = 60f
                        }
                    }

                    fun drawWrappedText(text: String, x: Float, width: Int, size: Float, isBold: Boolean = false) {
                        textPaint.textSize = size
                        textPaint.isFakeBoldText = isBold
                        val staticLayout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                            .build()
                        checkNewPage(staticLayout.height.toFloat() + 5f)
                        canvas.save()
                        canvas.translate(x, y)
                        staticLayout.draw(canvas)
                        canvas.restore()
                        y += staticLayout.height + 5f
                    }

                    // Header Text
                    drawWrappedText("INFORME INDIVIDUAL DE SEGUIMENTO", 40f, 515, 20f, true)
                    drawWrappedText("ALUMNO/A: ${alumno.nomeCompleto}", 40f, 515, 14f, true)
                    drawWrappedText("Período: $fechaInicio a $fechaFin", 40f, 515, 11f)
                    y += 10f

                    // Alumno Info
                    paint.color = android.graphics.Color.LTGRAY
                    canvas.drawRect(40f, y, 555f, y + 1f, paint)
                    paint.color = android.graphics.Color.BLACK
                    y += 15f
                    
                    drawWrappedText("Curso: ${alumno.curso} (${alumno.nivel})", 40f, 515, 11f)
                    drawWrappedText("Centro: ${alumno.centroEstudos}", 40f, 515, 11f)
                    
                    if (alumno.dificultadesAprendizaxe.isNotBlank()) {
                        drawWrappedText("Dificultades: ${alumno.dificultadesAprendizaxe}", 40f, 515, 10f)
                    }
                    if (alumno.medidasAtencion.isNotBlank()) {
                        drawWrappedText("Medidas: ${alumno.medidasAtencion}", 40f, 515, 10f)
                    }
                    y += 15f

                    // Valoracións Obxectivos
                    if (incluirObjetivos && objetivosRatings.isNotEmpty()) {
                        drawWrappedText("GRAO DE LOGO DOS OBXECTIVOS XERAIS DA ETAPA", 40f, 515, 13f, true)
                        y += 5f
                        objetivosRatings.forEach { (obj, rating) ->
                            drawWrappedText("• $obj: $rating", 50f, 485, 10f)
                        }
                        y += 15f
                    }

                    // Valoracións Competencias
                    if (incluirCompetencias && competenciasRatings.isNotEmpty()) {
                        drawWrappedText("ADQUISICIÓN DE COMPETENCIAS BÁSICAS", 40f, 515, 13f, true)
                        y += 5f
                        competenciasRatings.forEach { (comp, rating) ->
                            drawWrappedText("• $comp: $rating", 50f, 485, 10f)
                        }
                        y += 15f
                    }

                    // Rexistro Diario de Traballo
                    if (incluirTrabajo && registrosPeriodo.isNotEmpty()) {
                        drawWrappedText("REXISTRO DIARIO DE TRABALLO E EVOLUCIÓN", 40f, 515, 13f, true)
                        y += 5f
                        registrosPeriodo.forEach { r ->
                            drawWrappedText("[${r.fecha}] Traballo: ${r.traballoDia}", 50f, 485, 10f)
                            if (r.observacionDia.isNotBlank()) {
                                drawWrappedText("      Obs: ${r.observacionDia}", 50f, 485, 9f)
                            }
                        }
                        y += 15f
                    }
                    
                    // Rexistro Diario de Entregas
                    if (incluirEntregas && entregas.isNotEmpty()) {
                        drawWrappedText("REXISTRO DE ENTREGAS DE TRABALLOS", 40f, 515, 13f, true)
                        y += 5f
                        entregas.forEach { e ->
                            val materiaNome = materias.find { it.id == e.materiaId }?.nombre ?: "Materia"
                            drawWrappedText("[${e.fecha}] $materiaNome: ${e.descripcion}", 50f, 485, 10f)
                        }
                        y += 15f
                    }

                    // Asistencia
                    drawWrappedText("RESUMO DE ASISTENCIA", 40f, 515, 13f, true)
                    drawWrappedText("Total de sesións de apoio no período: ${asistenciaAlumno.size}", 50f, 485, 10f)
                    
                    if (asistenciaAlumno.isNotEmpty()) {
                        val diasIndividualLectivos = mutableListOf<LocalDate>()
                        var cDate = fechaInicio
                        while (!cDate.isAfter(fechaFin)) {
                            if (cDate.dayOfWeek.value < 6) diasIndividualLectivos.add(cDate)
                            cDate = cDate.plusDays(1)
                        }
                        val countsInd = diasIndividualLectivos.map { d -> if (asistenciaAlumno.any { it.fecha == d.toString() }) 1 else 0 }
                        
                        val cHeight = 40f
                        val cWidth = 515f
                        val cX = 40f
                        val cY = y + 5f
                        
                        if (diasIndividualLectivos.size > 1) {
                            paint.style = android.graphics.Paint.Style.STROKE
                            paint.strokeWidth = 1.5f
                            paint.color = android.graphics.Color.parseColor("#2196F3")
                            
                            val p = android.graphics.Path()
                            val spc = cWidth / (diasIndividualLectivos.size - 1)
                            
                            for (i in 0 until countsInd.size - 1) {
                                val x1 = cX + i * spc
                                val y1 = cY + cHeight - (countsInd[i].toFloat() * cHeight)
                                val x2 = cX + (i + 1) * spc
                                val y2 = cY + cHeight - (countsInd[i+1].toFloat() * cHeight)
                                
                                if (i == 0) p.moveTo(x1, y1)
                                val ctrlX = (x1 + x2) / 2f
                                p.cubicTo(ctrlX, y1, ctrlX, y2, x2, y2)
                            }
                            canvas.drawPath(p, paint)
                            
                            paint.style = android.graphics.Paint.Style.FILL
                            paint.color = android.graphics.Color.LTGRAY
                            canvas.drawLine(cX, cY + cHeight, cX + cWidth, cY + cHeight, paint)
                            
                            y += cHeight + 20f
                        }
                    }
                    y += 15f

                    // Observacións de seguimento
                    if (notas.isNotEmpty()) {
                        drawWrappedText("OBSERVACIÓNS DE SEGUIMENTO", 40f, 515, 13f, true)
                        y += 5f
                        notas.forEach { n ->
                            drawWrappedText("[${n.fecha}] ${n.tipo}: ${n.contenido}", 50f, 485, 10f)
                        }
                        y += 10f
                    }

                    // Comentarios adicionais
                    if (comentarios.isNotBlank()) {
                        drawWrappedText("COMENTARIOS ADICIONAIS", 40f, 515, 13f, true)
                        y += 5f
                        drawWrappedText(comentarios, 50f, 485, 10f)
                    }

                    pdfDocument.finishPage(page)
                    val outStream = java.io.ByteArrayOutputStream()
                    pdfDocument.writeTo(outStream)
                    pdfDocument.close()
                    val bytes = outStream.toByteArray()

                    procesarExportacion(fileName, relativePath, bytes, "application/pdf", prefs)
                } catch (e: Exception) {
                    Log.e("PDF", "Error xenerando PDF", e)
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    fun generarInformeCentros(centros: List<String>, fechaInicio: LocalDate, fechaFin: LocalDate, aulaId: String) {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
            val fileName = "Informe_Centros_${aulaId}_${System.currentTimeMillis()}.pdf"
            val relativePath = "Opah!/INFORMES/$aulaId"
            
            withContext(Dispatchers.IO) {
                try {
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    var pageNumber = 1
                    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    var page = pdfDocument.startPage(pageInfo)
                    var canvas = page.canvas
                    val paint = android.graphics.Paint()
                    val textPaint = android.text.TextPaint(paint)
                    var y = 30f

                    // Logo
                    val resId = context.resources.getIdentifier("logo_informe", "drawable", context.packageName)
                    if (resId != 0) {
                        BitmapFactory.decodeResource(context.resources, resId)?.let { logo ->
                            val logoWidth = 515f
                            val logoHeight = logoWidth * (logo.height.toFloat() / logo.width.toFloat())
                            canvas.drawBitmap(logo, null, android.graphics.RectF(40f, 20f, 40f + logoWidth, 20f + logoHeight), paint)
                            y = 20f + logoHeight + 30f
                        }
                    }

                    fun drawText(text: String, size: Float, isBold: Boolean = false, x: Float = 40f) {
                        textPaint.textSize = size
                        textPaint.isFakeBoldText = isBold
                        canvas.drawText(text, x, y, textPaint)
                        y += size + 10f
                    }

                    drawText("LISTADO DE CENTROS EDUCATIVOS", 18f, true)
                    drawText("Espazo: $aulaId", 12f)
                    drawText("Período: $fechaInicio a $fechaFin", 11f)
                    y += 20f
                    
                    paint.color = android.graphics.Color.BLACK
                    paint.strokeWidth = 1f
                    canvas.drawLine(40f, y, 555f, y, paint)
                    y += 25f

                    centros.forEach { centro ->
                        if (y > 780f) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            y = 50f
                        }
                        drawText("• $centro", 11f)
                    }

                    pdfDocument.finishPage(page)
                    val outStream = java.io.ByteArrayOutputStream()
                    pdfDocument.writeTo(outStream)
                    pdfDocument.close()
                    procesarExportacion(fileName, relativePath, outStream.toByteArray(), "application/pdf", prefs)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    fun generarInformeEventos(eventos: List<Evento>, fechaInicio: LocalDate, fechaFin: LocalDate, aulaId: String) {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
            val fileName = "Informe_Eventos_${aulaId}_${System.currentTimeMillis()}.pdf"
            val relativePath = "Opah!/INFORMES/$aulaId"
            
            withContext(Dispatchers.IO) {
                try {
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    var pageNumber = 1
                    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    var page = pdfDocument.startPage(pageInfo)
                    var canvas = page.canvas
                    val paint = android.graphics.Paint()
                    val textPaint = android.text.TextPaint(paint)
                    var y = 30f

                    // Logo
                    val resId = context.resources.getIdentifier("logo_informe", "drawable", context.packageName)
                    if (resId != 0) {
                        BitmapFactory.decodeResource(context.resources, resId)?.let { logo ->
                            val logoWidth = 515f
                            val logoHeight = logoWidth * (logo.height.toFloat() / logo.width.toFloat())
                            canvas.drawBitmap(logo, null, android.graphics.RectF(40f, 20f, 40f + logoWidth, 20f + logoHeight), paint)
                            y = 20f + logoHeight + 30f
                        }
                    }

                    fun drawText(text: String, size: Float, isBold: Boolean = false, x: Float = 40f) {
                        textPaint.textSize = size
                        textPaint.isFakeBoldText = isBold
                        canvas.drawText(text, x, y, textPaint)
                        y += size + 10f
                    }

                    drawText("LISTADO DE EVENTOS E REUNIÓNS", 18f, true)
                    drawText("Espazo: $aulaId", 12f)
                    drawText("Período: $fechaInicio a $fechaFin", 11f)
                    y += 20f
                    
                    paint.color = android.graphics.Color.BLACK
                    paint.strokeWidth = 1f
                    canvas.drawLine(40f, y, 555f, y, paint)
                    y += 25f

                    eventos.sortedBy { it.fecha }.forEach { ev ->
                        if (y > 750f) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            y = 50f
                        }
                        drawText("${ev.fecha} - ${ev.tipoEvento}", 10f, true)
                        // Descripción con wrapping simple o truncado
                        val desc = if (ev.descripcion.length > 80) ev.descripcion.take(77) + "..." else ev.descripcion
                        drawText(desc, 10f, x = 50f)
                        y += 10f
                    }

                    pdfDocument.finishPage(page)
                    val outStream = java.io.ByteArrayOutputStream()
                    pdfDocument.writeTo(outStream)
                    pdfDocument.close()
                    procesarExportacion(fileName, relativePath, outStream.toByteArray(), "application/pdf", prefs)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    fun generarInformeAsistencia(asistencia: List<Asistencia>, inicio: LocalDate, fin: LocalDate, aulaId: String) {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
            val curso = cursoActivo.value
            val fechaInicioCurso = curso?.fechaInicio ?: inicio.toString()
            
            val movimientos = repository.obtenerMovimientosPorAula(aulaId).first()
            
            // Calcular alumnos activos a final de período
            // Un alumno está activo se o seu último movemento en ou antes de 'fin' foi unha ALTA
            val activosAFinal = movimientos
                .filter { !LocalDate.parse(it.fecha).isAfter(fin) }
                .groupBy { it.alumnoId }
                .mapValues { (_, movs) -> movs.maxBy { it.fecha } }
                .count { it.value.tipo == "ALTA" }

            // Calcular alumnos acumulados desde o inicio do curso
            // Alumnos que tiveron algunha ALTA entre o inicio do curso e 'fin'
            val acumuladosCurso = movimientos
                .filter { 
                    val f = LocalDate.parse(it.fecha)
                    !f.isBefore(LocalDate.parse(fechaInicioCurso)) && !f.isAfter(fin) && it.tipo == "ALTA"
                }
                .map { it.alumnoId }
                .distinct()
                .size

            val fileName = "Informe_Asistencia_${aulaId}_${System.currentTimeMillis()}.pdf"
            val relativePath = "Opah!/INFORMES/$aulaId"
            
            withContext(Dispatchers.IO) {
                try {
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    var pageNumber = 1
                    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    var page = pdfDocument.startPage(pageInfo)
                    var canvas = page.canvas
                    val paint = android.graphics.Paint()
                    val textPaint = android.text.TextPaint(paint)
                    var y = 30f

                    // Logo
                    val resId = context.resources.getIdentifier("logo_informe", "drawable", context.packageName)
                    if (resId != 0) {
                        BitmapFactory.decodeResource(context.resources, resId)?.let { logo ->
                            val logoWidth = 515f
                            val logoHeight = logoWidth * (logo.height.toFloat() / logo.width.toFloat())
                            canvas.drawBitmap(logo, null, android.graphics.RectF(40f, 20f, 40f + logoWidth, 20f + logoHeight), paint)
                            y = 20f + logoHeight + 30f
                        }
                    }

                    fun drawText(text: String, size: Float, isBold: Boolean = false, x: Float = 40f) {
                        textPaint.textSize = size
                        textPaint.isFakeBoldText = isBold
                        canvas.drawText(text, x, y, textPaint)
                        y += size + 10f
                    }

                    drawText("RESUMO DE ASISTENCIA", 18f, true)
                    drawText("Espazo: $aulaId", 12f)
                    drawText("Período: $inicio a $fin", 11f)
                    y += 10f
                    
                    drawText("Alumnos activos a final de período: $activosAFinal", 11f, true)
                    drawText("Alumnos acumulados desde inicio de curso: $acumuladosCurso", 11f, true)
                    y += 10f
                    
                    paint.color = android.graphics.Color.BLACK
                    paint.strokeWidth = 1f
                    canvas.drawLine(40f, y, 555f, y, paint)
                    y += 25f

                    val totalSesiones = asistencia.size
                    drawText("Total de asistencias rexistradas no período: $totalSesiones", 12f, true)
                    y += 10f

                    // --- Gráfica de Asistencia (Sen fines de semana) ---
                    val diasChartLectivos = mutableListOf<LocalDate>()
                    var currDate = inicio
                    while (!currDate.isAfter(fin)) {
                        if (currDate.dayOfWeek.value < 6) diasChartLectivos.add(currDate)
                        currDate = currDate.plusDays(1)
                    }
                    val countsChart = diasChartLectivos.map { d -> asistencia.count { it.fecha == d.toString() } }
                    val maxCountChart = (countsChart.maxOrNull() ?: 1).coerceAtLeast(1)

                    val chartHeight = 120f
                    val chartWidth = 515f
                    val chartX = 40f
                    
                    if (diasChartLectivos.size > 1) {
                        val chartY = y + 10f
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 2f
                        paint.color = android.graphics.Color.parseColor("#2196F3")
                        
                        val path = android.graphics.Path()
                        val spacing = chartWidth / (diasChartLectivos.size - 1)
                        
                        for (i in 0 until countsChart.size - 1) {
                            val x1 = chartX + i * spacing
                            val y1 = chartY + chartHeight - (countsChart[i].toFloat() / maxCountChart.toFloat() * chartHeight)
                            val x2 = chartX + (i + 1) * spacing
                            val y2 = chartY + chartHeight - (countsChart[i+1].toFloat() / maxCountChart.toFloat() * chartHeight)
                            
                            if (i == 0) path.moveTo(x1, y1)
                            val controlX = (x1 + x2) / 2f
                            path.cubicTo(controlX, y1, controlX, y2, x2, y2)
                        }
                        canvas.drawPath(path, paint)
                        
                        // Eje X y etiquetas
                        paint.color = android.graphics.Color.LTGRAY
                        paint.strokeWidth = 1f
                        canvas.drawLine(chartX, chartY + chartHeight, chartX + chartWidth, chartY + chartHeight, paint)
                        
                        paint.style = android.graphics.Paint.Style.FILL
                        countsChart.forEachIndexed { i, count ->
                            val x = chartX + i * spacing
                            val py = chartY + chartHeight - (count.toFloat() / maxCountChart.toFloat() * chartHeight)
                            
                            paint.color = android.graphics.Color.parseColor("#FF4081")
                            canvas.drawCircle(x, py, 3f, paint)
                            
                            textPaint.textSize = 8f
                            textPaint.color = android.graphics.Color.DKGRAY
                            textPaint.textAlign = android.graphics.Paint.Align.CENTER
                            canvas.drawText(diasChartLectivos[i].dayOfMonth.toString(), x, chartY + chartHeight + 12f, textPaint)
                            
                            if (count > 0) {
                                canvas.drawText(count.toString(), x, py - 6f, textPaint)
                            }
                        }
                        textPaint.textAlign = android.graphics.Paint.Align.LEFT
                        y += chartHeight + 40f
                    }
                    paint.color = android.graphics.Color.BLACK
                    paint.style = android.graphics.Paint.Style.FILL
                    // -----------------------------

                    val porDia = asistencia.groupBy { it.fecha }.toSortedMap()
                    porDia.forEach { (fecha, lista) ->
                        if (y > 780f) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            y = 50f
                        }
                        drawText("$fecha: ${lista.size} alumnos", 11f)
                    }

                    pdfDocument.finishPage(page)
                    val outStream = java.io.ByteArrayOutputStream()
                    pdfDocument.writeTo(outStream)
                    pdfDocument.close()
                    procesarExportacion(fileName, relativePath, outStream.toByteArray(), "application/pdf", prefs)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    fun generarInformeODT(tipo: String, datos: Any, inicio: LocalDate, fin: LocalDate, aulaId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Xerando informe ODT de $tipo... (Próximamente)", Toast.LENGTH_SHORT).show()
            }
            // Aquí iría a lóxica de xerar un ficheiro .odt (ZIP con XMLs)
            // Polo de agora simulamos a subida dun ficheiro de texto plano como placeholder
            val prefs = preferencias.value ?: Preferencias()
            val fileName = "Informe_${tipo}_${aulaId}_${System.currentTimeMillis()}.odt"
            val relativePath = "Opah!/INFORMES/$aulaId"
            
            withContext(Dispatchers.IO) {
                try {
                    val content = StringBuilder().apply {
                        // ... (keep the builder logic)
                        append("INFORME DE ${tipo.uppercase()}\n")
                        append("Espazo: $aulaId\n")
                        append("Periodo: $inicio a $fin\n")
                        append("-------------------------------------------\n\n")
                        
                        when {
                            tipo == "Asistencia" && datos is List<*> -> {
                                val asistencias = datos.filterIsInstance<Asistencia>()
                                append("RESUMO DE ASISTENCIA UNIFICADO POR DATA:\n")
                                append("Total de asistencias no período: ${asistencias.size}\n\n")
                                
                                asistencias.groupBy { it.fecha }
                                    .toSortedMap()
                                    .forEach { (fecha, lista) ->
                                        append("Data: $fecha -> Total: ${lista.size} alumnos\n")
                                    }
                            }
                            tipo == "Centros" && datos is List<*> -> {
                                append("CENTROS EDUCATIVOS PRESENTES:\n")
                                datos.forEach { append("• $it\n") }
                            }
                            tipo == "Eventos" && datos is List<*> -> {
                                append("LISTADO DE EVENTOS E REUNIÓNS:\n")
                                datos.filterIsInstance<Evento>().sortedBy { it.fecha }.forEach { 
                                    append("• ${it.fecha} [${it.tipoEvento}]: ${it.descripcion}\n")
                                }
                            }
                            else -> append("Detalle de datos:\n$datos\n")
                        }
                        
                        append("\n-------------------------------------------\n")
                        append("Xerado automaticamente por Opah! - ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}")
                    }.toString()

                    procesarExportacion(fileName, relativePath, content.toByteArray(), "application/vnd.oasis.opendocument.text", prefs)
                } catch (e: Exception) {
                    Log.e("ODT", "Error", e)
                }
            }
        }
    }

    fun generarInformeAltaODT(
        alumno: Alumno,
        objetivosRatings: Map<String, String>,
        competenciasRatings: Map<String, String>,
        fechaInicio: LocalDate,
        fechaFin: LocalDate,
        incluirTrabajo: Boolean,
        incluirEntregas: Boolean,
        incluirObjetivos: Boolean,
        incluirCompetencias: Boolean,
        comentarios: String = ""
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Xerando informe ODT para ${alumno.nomeCompleto}...", Toast.LENGTH_SHORT).show()
            }
            // Simulación similar a generarInformeODT
            generarInformeODT("Individual_${alumno.nomeCompleto}", "Valoracions e rexistros varios. Comentarios: $comentarios", fechaInicio, fechaFin, alumno.aulaId)
        }
    }

    fun obtenerRegistroAcademico(alumnoId: Long, fecha: LocalDate): Flow<RegistroAcademico?> = repository.obtenerRegistroAcademico(alumnoId, fecha.toString())
    suspend fun obtenerUltimoRegistroAnterior(alumnoId: Long, fecha: LocalDate): RegistroAcademico? = repository.obtenerUltimoRegistroAnterior(alumnoId, fecha.toString())
    fun obtenerHistorialAcademico(alumnoId: Long): Flow<List<RegistroAcademico>> = repository.obtenerHistorialAcademico(alumnoId)
    fun obtenerAlertasPendientes(): Flow<List<AlertaPendiente>> = repository.obtenerAlertasPendientes()
    fun actualizarEstadoAlerta(alumnoId: Long, fecha: String, activa: Boolean) { viewModelScope.launch { repository.actualizarEstadoAlerta(alumnoId, fecha, activa) } }
    fun ocultarAlerta(alumnoId: Long, fecha: String) { viewModelScope.launch { repository.ocultarAlerta(alumnoId, fecha) } }
    fun guardarRegistroAcademico(registro: RegistroAcademico) { viewModelScope.launch { repository.insertarRegistroAcademico(registro) } }
    fun actualizarPreferencias(nuevasPreferencias: Preferencias) { viewModelScope.launch { repository.guardarPreferencias(nuevasPreferencias) } }

    fun obtenerAulaDiaria(aulaId: String, fecha: LocalDate): Flow<AulaDiaria?> = repository.obtenerAulaDiaria(aulaId, fecha.toString())
    fun guardarAulaDiaria(aulaDiaria: AulaDiaria) { viewModelScope.launch { repository.insertarAulaDiaria(aulaDiaria) } }

    fun insertarValoracionInforme(alumnoId: Long, fecha: LocalDate, item: String, valoracion: String, tipo: String) {
        viewModelScope.launch {
            repository.insertarValoracionInforme(ValoracionInforme(alumnoId, fecha.toString(), item, valoracion, tipo))
        }
    }

    fun obtenerValoracionsInforme(alumnoId: Long, fecha: LocalDate): Flow<List<ValoracionInforme>> = 
        repository.obtenerValoracionsInforme(alumnoId, fecha.toString())

    private suspend fun procesarExportacion(fileName: String, relativePath: String, content: ByteArray, mimeType: String, prefs: Preferencias) {
        if (prefs.destinoInformes == "LOCAL" || prefs.destinoInformes == "AMBOS") {
            guardarLocalmente(fileName, content, mimeType)
        }
        if (prefs.destinoInformes == "BOX" || prefs.destinoInformes == "AMBOS") {
            try {
                val client = OkHttpClient.Builder().protocols(listOf(Protocol.HTTP_1_1)).build()
                val auth = Credentials.basic(prefs.abalarboxUsuario.trim(), prefs.abalarboxClave.trim())
                val webDavBase = getWebDavUrl(prefs)
                subirABoxAbalar(relativePath, fileName, content, mimeType, prefs, auth, webDavBase, client)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error BoxAbalar: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun guardarLocalmente(fileName: String, content: ByteArray, mimeType: String) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Opah_Informes")
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(content)
                    }
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val opahDir = java.io.File(downloadsDir, "Opah_Informes")
                if (!opahDir.exists()) opahDir.mkdirs()
                val file = java.io.File(opahDir, fileName)
                FileOutputStream(file).use { it.write(content) }
            }
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Gardado localmente", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("LocalSave", "Error", e)
        }
    }

    private suspend fun subirABoxAbalar(relativePath: String, fileName: String, content: ByteArray, mimeType: String, prefs: Preferencias, auth: String, webDavBase: String, client: OkHttpClient) {
        listOf("Opah!", "Opah!/INFORMES", relativePath).forEach { dir ->
            client.newCall(Request.Builder().url(webDavBase + encode(dir)).header("Authorization", auth).method("MKCOL", null).build()).execute().close()
        }
        client.newCall(Request.Builder().url(webDavBase + encode("$relativePath/$fileName")).header("Authorization", auth).put(content.toRequestBody(mimeType.toMediaTypeOrNull())).build()).execute().use { response ->
            if (response.isSuccessful) withContext(Dispatchers.Main) { Toast.makeText(context, "Informe subido a BoxAbalar", Toast.LENGTH_SHORT).show() }
        }
    }
}
