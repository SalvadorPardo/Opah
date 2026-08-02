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
            repository.obtenerPreferencias().first()?.let { /* ya cargado por stateIn */ }
        }
    }

    fun verificarFinDeCurso(curso: Curso) {
        val hoy = LocalDate.now()
        val fin = try { LocalDate.parse(curso.fechaFin) } catch(e: Exception) { hoy }
        if (hoy.isAfter(fin) && curso.estaActivo) {
            viewModelScope.launch {
                repository.insertarCurso(curso.copy(estaActivo = false))
            }
        }
    }

    fun establecerCursoActivo(cursoId: Long) {
        viewModelScope.launch { repository.establecerCursoActivo(cursoId) }
    }

    fun insertarCurso(nombre: String, inicio: LocalDate, fin: LocalDate) {
        viewModelScope.launch {
            repository.insertarCurso(Curso(nombre = nombre, fechaInicio = inicio.toString(), fechaFin = fin.toString(), estaActivo = true))
        }
    }

    fun insertarFestivo(nombre: String, inicio: LocalDate, fin: LocalDate?, esLectivo: Boolean, cursoId: Long) {
        viewModelScope.launch {
            val dias = mutableListOf<DiaCalendario>()
            var actual = inicio
            val final = fin ?: inicio
            while (!actual.isAfter(final)) {
                dias.add(DiaCalendario(fecha = actual.toString(), cursoId = cursoId, esLectivo = esLectivo, nombreFestivo = if (esLectivo) null else nombre))
                actual = actual.plusDays(1)
            }
            repository.insertarDias(dias)
        }
    }

    fun insertarEvento(nombre: String, tipo: String, inicio: LocalDate, fin: LocalDate?, aulaId: String) {
        viewModelScope.launch {
            val cursoId = cursoActivo.value?.id ?: 0
            var actual = inicio
            val final = fin ?: inicio
            while (!actual.isAfter(final)) {
                repository.insertarEvento(Evento(fecha = actual.toString(), tipoEvento = tipo, aulaId = aulaId, descripcion = nombre, cursoId = cursoId))
                actual = actual.plusDays(1)
            }
        }
    }

    fun eliminarFestivosPorNombre(nombre: String, cursoId: Long) {
        // En DAO falta eliminarFestivosPorNombre, pero se puede emular con eliminarDia si tenemos los objetos.
        // Como no está en DAO, lo dejamos vacío o implementamos una alternativa si es crítico.
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
            repository.eliminarTodosLosMovimientos()
            repository.eliminarTodosLosRegistrosAcademicos()
        }
    }

    fun limpiarAlumnosDuplicados() {
        viewModelScope.launch {
            val todos = repository.obtenerTodosAlumnosPorAula("AMBAS").first()
            val grupos = todos.groupBy { it.nomeCompleto.lowercase().trim() + it.aulaId }
            grupos.forEach { (_, lista) ->
                if (lista.size > 1) {
                    val aMantener = lista.maxByOrNull { it.id } ?: return@forEach
                    lista.filter { it.id != aMantener.id }.forEach { aBorrar ->
                        repository.eliminarAlumno(aBorrar)
                    }
                }
            }
        }
    }

    fun obtenerAlumnos(aulaId: String, activos: Boolean): Flow<List<Alumno>> = repository.obtenerAlumnosPorAula(aulaId, activos)

    fun obtenerResumenAlumnos(): Flow<Map<String, Pair<Int, Int>>> {
        return repository.obtenerTodosAlumnosPorAula("AMBAS").map { lista ->
            val hd = lista.filter { it.aulaId == "HDDIJNP" }
            val us = lista.filter { it.aulaId == "USMIJHAC" }
            mapOf(
                "HDDIJNP" to (hd.count { it.esActivo } to hd.count { !it.esActivo }),
                "USMIJHAC" to (us.count { it.esActivo } to us.count { !it.esActivo })
            )
        }
    }

    fun obtenerAlumnosConEstadoEnFecha(aulaId: String, fecha: LocalDate): Flow<Pair<List<Alumno>, List<Alumno>>> {
        return repository.obtenerTodosAlumnosPorAula(aulaId).map { lista ->
            val activos = mutableListOf<Alumno>()
            val historicos = mutableListOf<Alumno>()
            
            lista.forEach { al ->
                val fCreacion = try { LocalDate.parse(al.fechaCreacion) } catch(_: Exception) { LocalDate.MIN }
                if (!fCreacion.isAfter(fecha)) {
                    if (al.esActivo) activos.add(al) else historicos.add(al)
                }
            }
            Pair(activos.sortedBy { it.nomeCompleto }, historicos.sortedBy { it.nomeCompleto })
        }
    }

    fun insertarAlumnoConMaterias(alumno: Alumno, materias: List<MateriaAlumno>) {
        viewModelScope.launch {
            val id = repository.insertarAlumno(alumno)
            materias.forEach { repository.insertarMateriaAlumno(it.copy(alumnoId = id)) }
            repository.insertarMovimientoAlumno(MovimientoAlumno(alumnoId = id, tipo = "ALTA", fecha = LocalDate.now().toString()))
        }
    }

    fun cambiarEstadoAlumno(alumnoId: Long, activo: Boolean, fecha: LocalDate) {
        viewModelScope.launch {
            repository.actualizarEstadoAlumno(alumnoId, activo)
            repository.insertarMovimientoAlumno(MovimientoAlumno(alumnoId = alumnoId, tipo = if (activo) "ALTA" else "BAIXA", fecha = fecha.toString()))
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

    fun insertarNotaAlumno(alumnoId: Long, tipo: String, contenido: String, fecha: LocalDate) {
        viewModelScope.launch { repository.insertarNotaAlumno(NotaAlumno(alumnoId = alumnoId, tipo = tipo, contenido = contenido, fecha = fecha.toString())) }
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
        val server = prefs.abalarboxServidor.trim().removeSuffix("/")
        return if (server.contains("/remote.php/dav/files/")) server else "$server/remote.php/dav/files/${prefs.abalarboxUsuario.trim()}/"
    }

    private fun getOcsBaseUrl(prefs: Preferencias): String {
        val server = prefs.abalarboxServidor.trim().removeSuffix("/")
        return if (server.contains("/ocs/v2.php/")) server else "$server/ocs/v2.php/apps/files_sharing/api/v1"
    }

    private fun encode(path: String) = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    fun realizarCopiaSeguridade() {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
            val dbFile = context.getDatabasePath("caderno_profesor_db")
            if (!dbFile.exists()) return@launch
            
            withContext(Dispatchers.IO) {
                try {
                    val content = dbFile.readBytes()
                    val fileName = "Copia_Seguridade_Opah_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.db"
                    
                    if (prefs.destinoInformes == "LOCAL" || prefs.destinoInformes == "AMBOS") {
                        guardarLocalmente(fileName, content, "application/x-sqlite3")
                    }
                    
                    if (prefs.destinoInformes == "BOX" || prefs.destinoInformes == "AMBOS") {
                        val client = OkHttpClient.Builder().protocols(listOf(Protocol.HTTP_1_1)).build()
                        val auth = Credentials.basic(prefs.abalarboxUsuario.trim(), prefs.abalarboxClave.trim())
                        val webDavBase = getWebDavUrl(prefs)
                        
                        val request = Request.Builder()
                            .url(webDavBase + encode("Opah!/BACKUPS/$fileName"))
                            .header("Authorization", auth)
                            .put(content.toRequestBody("application/x-sqlite3".toMediaTypeOrNull()))
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) withContext(Dispatchers.Main) { Toast.makeText(context, "Copia subida a BoxAbalar", Toast.LENGTH_SHORT).show() }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Backup", "Error", e)
                }
            }
        }
    }

    fun subirDocumentacionAlumno(alumno: Alumno, uris: List<Uri>) {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
            val alumnoCarpeta = alumno.nomeCompleto.replace(" ", "_")
            val relativePath = "Opah!/ALUMNOS/${alumno.aulaId}/$alumnoCarpeta/DOCUMENTACION"
            
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder().protocols(listOf(Protocol.HTTP_1_1)).build()
                    val auth = Credentials.basic(prefs.abalarboxUsuario.trim(), prefs.abalarboxClave.trim())
                    val webDavBase = getWebDavUrl(prefs)

                    uris.forEach { uri ->
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val content = inputStream.readBytes()
                            val fileName = uri.lastPathSegment ?: "doc_${System.currentTimeMillis()}"
                            
                            val request = Request.Builder()
                                .url(webDavBase + encode("$relativePath/$fileName"))
                                .header("Authorization", auth)
                                .put(content.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                .build()
                            
                            client.newCall(request).execute().close()
                        }
                    }
                    repository.insertarAlumno(alumno.copy(tieneDocumentacion = true))
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Documentación subida", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    Log.e("Upload", "Error", e)
                }
            }
        }
    }

    fun subirTrabajoMateria(alumno: Alumno, materia: MateriaAlumno, descripcion: String, uris: List<Uri>, fecha: LocalDate) {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
            val alumnoCarpeta = alumno.nomeCompleto.replace(" ", "_")
            val materiaCarpeta = materia.nombre.replace(" ", "_")
            val relativePath = "Opah!/ALUMNOS/${alumno.aulaId}/$alumnoCarpeta/TRABALLOS/$materiaCarpeta"
            
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder().protocols(listOf(Protocol.HTTP_1_1)).build()
                    val auth = Credentials.basic(prefs.abalarboxUsuario.trim(), prefs.abalarboxClave.trim())
                    val webDavBase = getWebDavUrl(prefs)

                    uris.forEach { uri ->
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val content = inputStream.readBytes()
                            val fileName = "${fecha}_${uri.lastPathSegment ?: "file"}"
                            val fullPath = "$relativePath/$fileName"
                            
                            // 1. Subir por WebDAV
                            val request = Request.Builder()
                                .url(webDavBase + encode(fullPath))
                                .header("Authorization", auth)
                                .put(content.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                .build()
                            
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    // 2. Obter enlace compartido (OCS API)
                                    val shareUrl = getOcsBaseUrl(prefs)
                                    val shareBody = FormBody.Builder()
                                        .add("path", "Opah!/ALUMNOS/${alumno.aulaId}/$alumnoCarpeta/TRABALLOS/$materiaCarpeta/$fileName")
                                        .add("shareType", "3") // Public link
                                        .build()
                                    
                                    val shareRequest = Request.Builder()
                                        .url(shareUrl)
                                        .header("Authorization", auth)
                                        .header("OCS-APIRequest", "true")
                                        .post(shareBody)
                                        .build()
                                    
                                    var nextcloudLink = ""
                                    client.newCall(shareRequest).execute().use { shareResponse ->
                                        val xml = shareResponse.body?.string() ?: ""
                                        nextcloudLink = xml.substringAfter("<url>", "").substringBefore("</url>", "")
                                    }

                                    // 3. Rexistrar na BD
                                    repository.insertarEntregaTrabajo(EntregaTrabajo(
                                        alumnoId = alumno.id,
                                        materiaId = materia.id,
                                        fecha = fecha.toString(),
                                        descripcion = descripcion,
                                        archivoNombre = fileName,
                                        urlNextcloud = nextcloudLink,
                                        canal = if (alumno.entregaCanalBoxabalar && alumno.entregaCanalEmail) "AMBOS" else if (alumno.entregaCanalEmail) "EMAIL" else "BOX"
                                    ))

                                    // 4. Enviar Email se procede
                                    if (alumno.entregaCanalEmail) {
                                        enviarEmailConTraballo(alumno, materia, descripcion, listOf(uri), prefs)
                                    }
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Traballo subido e rexistrado", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    Log.e("UploadTrabajo", "Error", e)
                }
            }
        }
    }

    private fun enviarEmailConTraballo(alumno: Alumno, materia: MateriaAlumno, descripcion: String, uris: List<Uri>, prefs: Preferencias) {
        val destino = if (alumno.contactoRecibeEntregas) alumno.contactoEmail else materia.email
        if (destino.isBlank()) return

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", prefs.emailSmtpServidor)
            put("mail.smtp.port", prefs.emailSmtpPuerto.toString())
        }

        val session = Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(prefs.emailDireccion, prefs.emailClave)
            }
        })

        try {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(prefs.emailDireccion))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destino))
            message.subject = "Entrega de Traballo: ${alumno.nomeCompleto} - ${materia.nombre}"

            val body = MimeBodyPart()
            body.setText("Enviase o traballo de ${alumno.nomeCompleto} para a materia de ${materia.nombre}.\n\nDescrición: $descripcion\n\nEnviado desde Opah!")

            val multipart = MimeMultipart()
            multipart.addBodyPart(body)

            uris.forEach { uri ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val attachment = MimeBodyPart()
                    val source = ByteArrayDataSource(inputStream.readBytes(), context.contentResolver.getType(uri) ?: "application/octet-stream")
                    attachment.dataHandler = DataHandler(source)
                    attachment.fileName = uri.lastPathSegment ?: "traballo"
                    multipart.addBodyPart(attachment)
                }
            }

            message.setContent(multipart)
            Transport.send(message)
        } catch (e: Exception) {
            Log.e("Email", "Error enviando email", e)
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

                    fun drawTabulatedText(label: String, value: String, x: Float, labelWidth: Int, valueX: Float, size: Float) {
                        textPaint.textSize = size
                        textPaint.isFakeBoldText = false
                        val staticLayout = android.text.StaticLayout.Builder.obtain(label, 0, label.length, textPaint, labelWidth)
                            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                            .build()
                        checkNewPage(staticLayout.height.toFloat() + 5f)
                        canvas.save()
                        canvas.translate(x, y)
                        staticLayout.draw(canvas)
                        canvas.restore()
                        
                        val baseline = staticLayout.getLineBaseline(0).toFloat()
                        canvas.drawText(value, valueX, y + baseline, textPaint)
                        
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
                        drawWrappedText("GRAO DE LOGRO DOS OBXECTIVOS XERAIS DA ETAPA", 40f, 515, 13f, true)
                        y += 5f
                        objetivosRatings.toSortedMap().forEach { (obj, rating) ->
                            drawTabulatedText(obj, rating, 50f, 440, 500f, 10f)
                        }
                        y += 15f
                    }

                    // Valoracións Competencias
                    if (incluirCompetencias && competenciasRatings.isNotEmpty()) {
                        drawWrappedText("ADQUISICIÓN DE COMPETENCIAS BÁSICAS", 40f, 515, 13f, true)
                        y += 5f
                        competenciasRatings.toSortedMap().forEach { (comp, rating) ->
                            drawTabulatedText("• $comp", rating, 50f, 440, 500f, 10f)
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
                        drawWrappedText(comentarios, 50f, 485, 10f)
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

                    eventos.sortedBy { it.fecha }.forEach { evento ->
                        if (y > 780f) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            y = 50f
                        }
                        drawText("[${evento.fecha}] ${evento.tipoEvento}: ${evento.descripcion}", 11f)
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

    fun generarInformeAsistencia(asistencias: List<Asistencia>, fechaInicio: LocalDate, fechaFin: LocalDate, aulaId: String) {
        viewModelScope.launch {
            val prefs = preferencias.value ?: Preferencias()
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

                    drawText("RESUMO DE ASISTENCIA UNIFICADO POR DATA", 18f, true)
                    drawText("Espazo: $aulaId", 12f)
                    drawText("Período: $fechaInicio a $fechaFin", 11f)
                    y += 10f
                    drawText("Total de asistencias no período: ${asistencias.size}", 11f)
                    y += 10f
                    
                    paint.color = android.graphics.Color.BLACK
                    paint.strokeWidth = 1f
                    canvas.drawLine(40f, y, 555f, y, paint)
                    y += 25f

                    asistencias.groupBy { it.fecha }.toSortedMap().forEach { (fecha, lista) ->
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
            val prefs = preferencias.value ?: Preferencias()
            val fileName = "Informe_${tipo}_${aulaId}_${System.currentTimeMillis()}.odt"
            val relativePath = "Opah!/INFORMES/$aulaId"
            
            withContext(Dispatchers.IO) {
                try {
                    val logoBase64 = getLogoBase64()
                    val htmlContent = StringBuilder().apply {
                        append("<div class=\"header\">")
                        append("<div class=\"title\">OPAH! - INFORME DE ${tipo.uppercase()}</div>")
                        append("<div>Espazo: $aulaId | Período: $inicio a $fin</div>")
                        append("</div>")
                        
                        when {
                            tipo == "Asistencia" && datos is List<*> -> {
                                val asistencias = datos.filterIsInstance<Asistencia>()
                                append("<div class=\"section\">RESUMO DE ASISTENCIA</div>")
                                append("<p>Total de asistencias no período: <b>${asistencias.size}</b></p>")
                                append("<table><tr><th>Data</th><th>Total Alumnos</th></tr>")
                                asistencias.groupBy { it.fecha }
                                    .toSortedMap()
                                    .forEach { (fecha, lista) ->
                                        append("<tr><td>$fecha</td><td>${lista.size}</td></tr>")
                                    }
                                append("</table>")
                            }
                            tipo == "Centros" && datos is List<*> -> {
                                append("<div class=\"section\">CENTROS EDUCATIVOS PRESENTES</div>")
                                append("<ul>")
                                datos.forEach { append("<li>$it</li>") }
                                append("</ul>")
                            }
                            tipo == "Eventos" && datos is List<*> -> {
                                append("<div class=\"section\">LISTADO DE EVENTOS E REUNIÓNS</div>")
                                append("<table><tr><th>Data</th><th>Tipo</th><th>Descrición</th></tr>")
                                datos.filterIsInstance<Evento>().sortedBy { it.fecha }.forEach { 
                                    append("<tr><td>${it.fecha}</td><td>${it.tipoEvento}</td><td>${it.descripcion}</td></tr>")
                                }
                                append("</table>")
                            }
                            tipo.startsWith("Individual") -> {
                                append(datos.toString())
                            }
                            else -> append("<p>Detalle de datos: $datos</p>")
                        }
                    }.toString()

                    val odtBytes = createOdtZip(htmlContent, logoBase64)
                    procesarExportacion(fileName, relativePath, odtBytes, "application/vnd.oasis.opendocument.text", prefs)
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

            val individualContent = StringBuilder().apply {
                append("<div style=\"font-size: 18px; margin-bottom: 10px;\"><b>ALUMNO/A:</b> ${alumno.nomeCompleto}</div>")
                append("<div style=\"margin-bottom: 20px;\"><b>Curso:</b> ${alumno.curso} (${alumno.nivel}) | <b>Centro:</b> ${alumno.centroEstudos}</div>")
                
                if (alumno.dificultadesAprendizaxe.isNotBlank()) {
                    append("<p><b>Dificultades:</b> ${alumno.dificultadesAprendizaxe.replace("\n", ", ")}</p>")
                }
                if (alumno.medidasAtencion.isNotBlank()) {
                    append("<p><b>Medidas:</b> ${alumno.medidasAtencion}</p>")
                }

                if (incluirObjetivos && objetivosRatings.isNotEmpty()) {
                    append("<div class=\"section\">GRAO DE LOGRO DOS OBXECTIVOS XERAIS DA ETAPA</div>")
                    append("<table><tr><th>Obxectivo</th><th>Valoración</th></tr>")
                    objetivosRatings.toSortedMap().forEach { (obj, rating) ->
                        append("<tr><td>$obj</td><td>$rating</td></tr>")
                    }
                    append("</table>")
                }

                if (incluirCompetencias && competenciasRatings.isNotEmpty()) {
                    append("<div class=\"section\">ADQUISICIÓN DE COMPETENCIAS BÁSICAS</div>")
                    append("<table><tr><th>Competencia</th><th>Valoración</th></tr>")
                    competenciasRatings.toSortedMap().forEach { (comp, rating) ->
                        append("<tr><td>$comp</td><td>$rating</td></tr>")
                    }
                    append("</table>")
                }

                if (incluirTrabajo && registrosPeriodo.isNotEmpty()) {
                    append("<div class=\"section\">REXISTRO DIARIO DE TRABALLO E EVOLUCIÓN</div>")
                    registrosPeriodo.forEach { r ->
                        append("<p><b>[${r.fecha}]</b> ${r.traballoDia}")
                        if (r.observacionDia.isNotBlank()) {
                            append("<br/><i>Obs: ${r.observacionDia}</i>")
                        }
                        append("</p>")
                    }
                }

                if (incluirEntregas && entregas.isNotEmpty()) {
                    append("<div class=\"section\">REXISTRO DE ENTREGAS DE TRABALLOS</div>")
                    append("<ul>")
                    entregas.forEach { e ->
                        val materiaNome = materias.find { it.id == e.materiaId }?.nombre ?: "Materia"
                        append("<li><b>[${e.fecha}] $materiaNome:</b> ${e.descripcion}</li>")
                    }
                    append("</ul>")
                }

                append("<div class=\"section\">RESUMO DE ASISTENCIA</div>")
                append("<p>Total de sesións de apoio no período: <b>${asistenciaAlumno.size}</b></p>")

                if (notas.isNotEmpty()) {
                    append("<div class=\"section\">OBSERVACIÓNS DE SEGUIMENTO</div>")
                    notas.forEach { n ->
                        append("<p><b>[${n.fecha}] ${n.tipo}:</b> ${n.contenido}</p>")
                    }
                }

                if (comentarios.isNotBlank()) {
                    append("<div class=\"section\">COMENTARIOS ADICIONAIS</div>")
                    append("<p>${comentarios}</p>")
                }
            }.toString()

            generarInformeODT("Individual_${alumno.nomeCompleto}", individualContent, fechaInicio, fechaFin, alumno.aulaId)
        }
    }

    fun generarInformePeriodoZip(fechaInicio: LocalDate, fechaFin: LocalDate) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Preparando ZIP de informes...", Toast.LENGTH_SHORT).show()
            }
            
            val prefs = preferencias.value ?: Preferencias()
            val curso = cursoActivo.value
            val fechaInicioCurso = try { LocalDate.parse(curso?.fechaInicio ?: "2024-09-01") } catch(e: Exception) { LocalDate.of(2024, 9, 1) }
            
            val fileName = "Informe_Periodo_${fechaInicio}_${fechaFin}.zip"
            val relativePath = "Opah!/INFORMES"
            
            withContext(Dispatchers.IO) {
                try {
                    val baos = java.io.ByteArrayOutputStream()
                    val zos = java.util.zip.ZipOutputStream(baos)
                    val logoBase64 = getLogoBase64()
                    
                    val aulas = listOf("HDDIJNP", "USMIJHAC")
                    
                    aulas.forEach { aulaId ->
                        val asistenciasPeriodo = repository.obtenerAsistenciaRango(fechaInicio.toString(), fechaFin.toString(), aulaId).first()
                        val idsAlumnosPeriodo = asistenciasPeriodo.map { it.alumnoId }.toSet()
                        
                        val todosAlumnosAula = repository.obtenerTodosAlumnosPorAula(aulaId).first()
                        val alumnosActivosEnPeriodo = todosAlumnosAula.filter { it.id in idsAlumnosPeriodo }
                        
                        val asistenciasCurso = repository.obtenerAsistenciaRango(fechaInicioCurso.toString(), fechaFin.toString(), aulaId).first()
                        val totalAcumulado = asistenciasCurso.map { it.alumnoId }.distinct().size

                        val todosMovimientos = mutableListOf<MovimientoAlumno>()
                        todosAlumnosAula.forEach { al ->
                            todosMovimientos.addAll(repository.obtenerMovimientosAlumno(al.id).first())
                        }

                        val movimientosPeriodo = todosMovimientos.filter { mov ->
                            val f = try { LocalDate.parse(mov.fecha) } catch(e: Exception) { null }
                            (f != null && !f.isBefore(fechaInicio) && !f.isAfter(fechaFin))
                        }

                        val summaryContent = StringBuilder().apply {
                            append("<div class=\"header\"><div class=\"title\">RESUMO DE ACTIVIDADE - $aulaId</div>")
                            append("<div>Período: $fechaInicio a $fechaFin</div></div>")
                            
                            append("<h3>Movementos no período (Altas e Baixas)</h3>")
                            append("<table><tr><th>Data</th><th>Tipo</th><th>Alumno/a</th><th>Centro</th><th>Curso/Nivel</th></tr>")
                            movimientosPeriodo.sortedBy { it.fecha }.forEach { mov ->
                                val al = todosAlumnosAula.find { it.id == mov.alumnoId }
                                if (al != null) {
                                    append("<tr><td>${mov.fecha}</td><td>${mov.tipo}</td><td>${al.nomeCompleto}</td><td>${al.centroEstudos}</td><td>${al.curso} ${al.nivel}</td></tr>")
                                }
                            }
                            append("</table>")
                            
                            append("<div style=\"margin-top: 20px;\">")
                            append("<p><b>Alumnos activos no período:</b> ${alumnosActivosEnPeriodo.size}</p>")
                            append("<p><b>Alumnos acumulados desde inicio curso ($fechaInicioCurso):</b> $totalAcumulado</p>")
                            append("</div>")
                        }.toString()
                        
                        zos.putNextEntry(java.util.zip.ZipEntry("Resumo_$aulaId.odt"))
                        zos.write(createOdtZip(summaryContent, logoBase64))
                        zos.closeEntry()
                        
                        alumnosActivosEnPeriodo.forEach { alumno ->
                            val historial = repository.obtenerHistorialAcademico(alumno.id).first()
                            val registros = historial.filter {
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

                            val individualContent = StringBuilder().apply {
                                append("<div class=\"header\"><div class=\"title\">INFORME DE SEGUIMENTO</div>")
                                append("<div>${alumno.nomeCompleto} | $fechaInicio a $fechaFin</div></div>")
                                
                                append("<div class=\"section\">REXISTRO DIARIO DE TRABALLO</div>")
                                registros.forEach { r ->
                                    append("<p><b>[${r.fecha}]</b> ${r.traballoDia}")
                                    if (r.observacionDia.isNotBlank()) append("<br/><i>Obs: ${r.observacionDia}</i>")
                                    append("</p>")
                                }
                                
                                append("<div class=\"section\">REXISTRO DE ENTREGAS</div>")
                                append("<ul>")
                                entregas.forEach { e ->
                                    val mNome = materias.find { it.id == e.materiaId }?.nombre ?: "Materia"
                                    append("<li><b>[${e.fecha}] $mNome:</b> ${e.descripcion}</li>")
                                }
                                append("</ul>")
                            }.toString()
                            
                            val safeName = alumno.nomeCompleto.replace(" ", "_")
                            zos.putNextEntry(java.util.zip.ZipEntry("$aulaId/Informe_$safeName.odt"))
                            zos.write(createOdtZip(individualContent, logoBase64))
                            zos.closeEntry()
                        }
                    }
                    
                    zos.finish()
                    zos.close()
                    
                    procesarExportacion(fileName, relativePath, baos.toByteArray(), "application/zip", prefs)
                } catch (e: Exception) {
                    Log.e("ZIP", "Error", e)
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Erro ao xerar ZIP: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    fun obtenerRegistroAcademico(alumnoId: Long, fecha: LocalDate): Flow<RegistroAcademico?> = repository.obtenerRegistroAcademico(alumnoId, fecha.toString())

    private fun createOdtZip(htmlContent: String, logoBase64: String?): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(baos)
        
        // 1. mimetype (MUST be first, uncompressed, no extra fields)
        val mimetypeBytes = "application/vnd.oasis.opendocument.text".toByteArray(Charsets.US_ASCII)
        val mimetypeEntry = java.util.zip.ZipEntry("mimetype").apply {
            method = java.util.zip.ZipOutputStream.STORED
            size = mimetypeBytes.size.toLong()
            compressedSize = mimetypeBytes.size.toLong()
            crc = java.util.zip.CRC32().apply { update(mimetypeBytes) }.value
        }
        zos.putNextEntry(mimetypeEntry)
        zos.write(mimetypeBytes)
        zos.closeEntry()

        // 2. META-INF/manifest.xml
        zos.putNextEntry(java.util.zip.ZipEntry("META-INF/manifest.xml"))
        val manifest = """
            <?xml version="1.0" encoding="UTF-8"?>
            <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
                <manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="application/vnd.oasis.opendocument.text"/>
                <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
                <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
                <manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>
                <manifest:file-entry manifest:full-path="settings.xml" manifest:media-type="text/xml"/>
                ${if (logoBase64 != null) "<manifest:file-entry manifest:full-path=\"Pictures/logo.png\" manifest:media-type=\"image/png\"/>" else ""}
            </manifest:manifest>
        """.trimIndent()
        zos.write(manifest.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 3. meta.xml
        zos.putNextEntry(java.util.zip.ZipEntry("meta.xml"))
        val meta = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0" office:version="1.2">
                <office:meta>
                    <meta:generator>Opah! Android</meta:generator>
                    <meta:creation-date>${java.time.ZonedDateTime.now()}</meta:creation-date>
                </office:meta>
            </office:document-meta>
        """.trimIndent()
        zos.write(meta.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 4. settings.xml (Minimal)
        zos.putNextEntry(java.util.zip.ZipEntry("settings.xml"))
        val settings = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-settings xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:config="urn:oasis:names:tc:opendocument:xmlns:config:1.0" office:version="1.2">
                <office:settings/>
            </office:document-settings>
        """.trimIndent()
        zos.write(settings.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 5. styles.xml
        zos.putNextEntry(java.util.zip.ZipEntry("styles.xml"))
        val styles = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:version="1.2">
                <office:styles>
                    <style:default-style style:family="paragraph">
                        <style:paragraph-properties fo:line-height="120%"/>
                        <style:text-properties fo:font-size="11pt" fo:language="gl" fo:country="ES"/>
                    </style:default-style>
                </office:styles>
            </office:document-styles>
        """.trimIndent()
        zos.write(styles.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 6. Pictures/logo.png
        if (logoBase64 != null) {
            zos.putNextEntry(java.util.zip.ZipEntry("Pictures/logo.png"))
            zos.write(android.util.Base64.decode(logoBase64, android.util.Base64.NO_WRAP))
            zos.closeEntry()
        }

        // 7. content.xml
        zos.putNextEntry(java.util.zip.ZipEntry("content.xml"))
        val bodyXml = htmlToOdtXml(htmlContent)
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content 
                xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" 
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" 
                xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" 
                xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" 
                xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
                xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                xmlns:xlink="http://www.w3.org/1999/xlink" 
                xmlns:svg="http://www.w3.org/2000/svg" 
                office:version="1.2">
                <office:automatic-styles>
                    <style:style style:name="Bold" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>
                    <style:style style:name="Italic" style:family="text"><style:text-properties fo:font-style="italic"/></style:style>
                    <style:style style:name="Title" style:family="paragraph"><style:paragraph-properties fo:text-align="center"/><style:text-properties fo:font-size="20pt" fo:font-weight="bold" fo:color="#2196F3"/></style:style>
                    <style:style style:name="Section" style:family="paragraph"><style:paragraph-properties fo:margin-top="0.2in" fo:margin-bottom="0.05in" fo:border-bottom="0.5pt solid #cccccc"/><style:text-properties fo:font-weight="bold" fo:color="#1976D2"/></style:style>
                </office:automatic-styles>
                <office:body>
                    <office:text>
                        ${if (logoBase64 != null) """
                        <text:p>
                            <draw:frame draw:name="logo" text:anchor-type="paragraph" svg:width="17cm" svg:height="2.5cm" draw:z-index="0">
                                <draw:image xlink:href="Pictures/logo.png" xlink:show="embed" xlink:actuate="onLoad"/>
                            </draw:frame>
                        </text:p>
                        """.trimIndent() else ""}
                        $bodyXml
                        <text:p text:style-name="Section">Xerado automaticamente por Opah! - ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}</text:p>
                    </office:text>
                </office:body>
            </office:document-content>
        """.trimIndent()
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
        
        zos.close()
        return baos.toByteArray()
    }

    private fun htmlToOdtXml(html: String): String {
        var tableCounter = 1
        return html
            .replace("<div class=\"header\">", "").replace("</div>", "")
            .replace("<div class=\"title\">(.*?)</div>".toRegex(), "<text:p text:style-name=\"Title\">$1</text:p>")
            .replace("<div class=\"section\">(.*?)</div>".toRegex(), "<text:p text:style-name=\"Section\">$1</text:p>")
            .replace("<p>(.*?)</p>".toRegex(), "<text:p>$1</text:p>")
            .replace("<h3>(.*?)</h3>".toRegex(), "<text:h text:outline-level=\"3\">$1</text:h>")
            .replace("<b>(.*?)</b>".toRegex(), "<text:span text:style-name=\"Bold\">$1</text:span>")
            .replace("<i>(.*?)</i>".toRegex(), "<text:span text:style-name=\"Italic\">$1</text:span>")
            .replace(Regex("<table>")) { "<table:table table:name=\"Table${tableCounter++}\">" }
            .replace("</table>", "</table:table>")
            .replace("<tr>", "<table:table-row>")
            .replace("</tr>", "</table:table-row>")
            .replace("<th>(.*?)</th>".toRegex(), "<table:table-cell office:value-type=\"string\"><text:p text:style-name=\"Bold\">$1</text:p></table:table-cell>")
            .replace("<td>(.*?)</td>".toRegex(), "<table:table-cell office:value-type=\"string\"><text:p>$1</text:p></table:table-cell>")
            .replace("<br/>", "<text:line-break/>")
            .replace("<ul>", "").replace("</ul>", "")
            .replace("<li>(.*?)</li>".toRegex(), "<text:p>• $1</text:p>")
    }

    private fun getLogoBase64(): String? {
        return try {
            val resId = context.resources.getIdentifier("logo_informe", "drawable", context.packageName)
            if (resId != 0) {
                val inputStream = context.resources.openRawResource(resId)
                val bytes = inputStream.readBytes()
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } else null
        } catch (e: Exception) { null }
    }

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
