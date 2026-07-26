package com.example.cadernodoprofesor.ui.alumnos

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cadernodoprofesor.data.Alumno
import com.example.cadernodoprofesor.data.MateriaAlumno
import com.example.cadernodoprofesor.ui.calendario.CalendarioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

@Composable
fun AlumnoFicheiroScreen(viewModel: CalendarioViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uriSeleccionada by remember { mutableStateOf<Uri?>(null) }
    var nombreFichero by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<ImportSummary?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uriSeleccionada = uri
        uri?.let {
            nombreFichero = getFileName(context, it)
        }
        summary = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FileUpload,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Importar Alumnos dende ODS",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Selecciona un ficheiro .ods para dar de alta a varios alumnos a partir das follas do documento.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Button(
            onClick = { pickerLauncher.launch("application/vnd.oasis.opendocument.spreadsheet") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uriSeleccionada == null) "Seleccionar Ficheiro ODS" else "Cambiar Ficheiro")
        }

        uriSeleccionada?.let { uri ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ficheiro seleccionado:", style = MaterialTheme.typography.labelSmall)
                    Text(nombreFichero, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        procesando = true
                        val result = importarAlumnoDesdeOds(context, uri, viewModel)
                        summary = result
                        procesando = false
                        if (!result.error && result.imported.isNotEmpty()) {
                            uriSeleccionada = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !procesando
            ) {
                if (procesando) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Procesar e Importar")
                }
            }
        }

        summary?.let { s ->
            if (s.error) {
                Text("Erro crítico ao procesar o ficheiro.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resultado da importación:", fontWeight = FontWeight.Bold)
                        Text("Total de follas: ${s.totalSheets}")
                        Text("Importados: ${s.imported.size}", color = MaterialTheme.colorScheme.primary)
                        if (s.skipped.isNotEmpty()) {
                            Text("Saltados (sen áncora): ${s.skipped.size}", color = MaterialTheme.colorScheme.error)
                            Text("Follas omitidas: ${s.skipped.joinToString(", ")}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Formato do ficheiro ODS:", fontWeight = FontWeight.Bold)
                Text("O sistema busca o inicio dos datos en cada folla onde apareza 'HDDIJNP' ou 'USMIJHAC' na Columna A ou B.", style = MaterialTheme.typography.bodySmall)
                Text("Os campos (Nome, Centro, Materias...) deben estar na mesma columna, en filas sucesivas a partir da áncora.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

data class ImportSummary(
    val totalSheets: Int,
    val imported: List<String>,
    val skipped: List<String>,
    val error: Boolean = false
)


private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result ?: "Ficheiro ODS"
}

private suspend fun importarAlumnoDesdeOds(context: Context, uri: Uri, viewModel: CalendarioViewModel): ImportSummary {
    return withContext(Dispatchers.IO) {
        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext ImportSummary(0, emptyList(), emptyList(), true)
            val sheets = parseOdsSmartScanner(inputStream)
            
            if (sheets.isEmpty()) return@withContext ImportSummary(0, emptyList(), emptyList())

            for (sheet in sheets) {
                val rows = sheet.second
                var foundAnchor = false
                
                // Buscamos a áncora en calquera das primeiras 5 columnas
                for (colIdx in 0 until 5) {
                    val anchorRow = rows.indexOfFirst { row ->
                        val cellValue = row.getOrNull(colIdx)?.trim()?.uppercase() ?: ""
                        cellValue == "HDDIJNP" || cellValue == "USMIJHAC"
                    }

                    if (anchorRow != -1) {
                        foundAnchor = true
                        val aulaId = rows[anchorRow][colIdx].trim().uppercase()
                        
                        fun getV(rowRel: Int): String {
                            val raw = rows.getOrNull(anchorRow + rowRel)?.getOrNull(colIdx)?.trim() ?: ""
                            // Eliminamos posibles etiquetas XML residuais e caracteres de control
                            return raw.replace(Regex("<[^>]*>"), "")
                                .replace("\u2028", " ")
                                .replace("\u2029", " ")
                        }

                        val rawRef = getV(5)
                        val refMapped = when {
                            rawRef.contains("Tutor", ignoreCase = true) || rawRef.contains("Titor", ignoreCase = true) -> "Titor/a"
                            rawRef.contains("Orientador", ignoreCase = true) -> "Orientador/a"
                            else -> rawRef
                        }

                        val alumno = Alumno(
                            aulaId = aulaId,
                            nomeCompleto = getV(1),
                            centroEstudos = getV(2),
                            curso = getV(3),
                            nivel = getV(4),
                            contactoReferencia = refMapped,
                            contactoNome = getV(6),
                            contactoEmail = getV(7),
                            contactoTelefono = getV(8),
                            dificultadesAprendizaxe = getV(9),
                            medidasAtencion = getV(10),
                            obxetivosXerais = getV(11),
                            fechaEval1 = getV(12),
                            fechaEval2 = getV(13),
                            fechaEval3 = getV(14),
                            fechaCreacion = LocalDate.now().toString()
                        )

                        if (alumno.nomeCompleto.isNotBlank()) {
                            val materias = mutableListOf<MateriaAlumno>()
                            var j = 15
                            while (anchorRow + j + 3 < rows.size) {
                                val nm = getV(j)
                                if (nm.isNotBlank() && nm.length > 2 && nm.uppercase() != "MATERIAS") {
                                    materias.add(MateriaAlumno(
                                        alumnoId = 0,
                                        nombre = nm,
                                        profesor = getV(j + 1),
                                        email = getV(j + 2),
                                        objetivos = getV(j + 3)
                                    ))
                                }
                                j += 4
                                if (j > 100) break
                            }
                            viewModel.insertarAlumnoConMaterias(alumno, materias)
                            imported.add(sheet.first)
                        } else {
                            skipped.add(sheet.first)
                        }
                        break // Pasamos á seguinte folla se xa atopamos o alumno nesta columna
                    }
                }
                if (!foundAnchor) {
                    skipped.add(sheet.first)
                }
            }
            ImportSummary(sheets.size, imported, skipped)
        } catch (e: Exception) {
            Log.e("ODS", "Error importando ODS", e)
            ImportSummary(0, imported, skipped, true)
        }
    }
}

private fun parseOdsSmartScanner(inputStream: InputStream): List<Pair<String, List<List<String>>>> {
    val allSheets = mutableListOf<Pair<String, List<List<String>>>>()
    try {
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "content.xml") {
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(zip, "UTF-8")

                    var eventType = parser.eventType
                    var currentSheetName = ""
                    var currentSheetRows = mutableListOf<List<String>>()
                    
                    var currentRowCells = mutableListOf<String>()
                    var currentRowRepeated = 1
                    var inCell = false
                    val cellContent = StringBuilder()
                    var currentColRepeat = 1

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                val tagName = parser.name
                                when (tagName) {
                                    "table:table" -> {
                                        currentSheetName = parser.getAttributeValue(null, "table:name") ?: "Sen nome"
                                        currentSheetRows = mutableListOf<List<String>>()
                                    }
                                    "table:table-row" -> {
                                        currentRowCells = mutableListOf()
                                        currentRowRepeated = parser.getAttributeValue(null, "table:number-rows-repeated")?.toIntOrNull() ?: 1
                                    }
                                    "table:table-cell" -> {
                                        inCell = true
                                        cellContent.setLength(0)
                                        currentColRepeat = parser.getAttributeValue(null, "table:number-columns-repeated")?.toIntOrNull() ?: 1
                                    }
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (inCell) {
                                    cellContent.append(parser.text)
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                val tagName = parser.name
                                if (tagName == "text:p" && inCell) {
                                    cellContent.append("\n")
                                }
                                when (tagName) {
                                    "table:table-cell" -> {
                                        inCell = false
                                        val value = cellContent.toString().trim()
                                        repeat(currentColRepeat) {
                                            if (currentRowCells.size < 10) { // Limitamos a 10 columnas por eficiencia
                                                currentRowCells.add(value)
                                            }
                                        }
                                    }
                                    "table:table-row" -> {
                                        // Só engadimos filas se teñen algún contido ou se non superamos un límite razoable
                                        if (currentSheetRows.size < 1000) {
                                            val remaining = 1000 - currentSheetRows.size
                                            val toAdd = if (currentRowRepeated > remaining) remaining else currentRowRepeated
                                            val rowToStore = currentRowCells.toList()
                                            repeat(toAdd) {
                                                currentSheetRows.add(rowToStore)
                                            }
                                        }
                                    }
                                    "table:table" -> {
                                        if (currentSheetRows.any { row -> row.any { it.isNotBlank() } }) {
                                            allSheets.add(currentSheetName to currentSheetRows)
                                        }
                                    }
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }
    } catch (e: Exception) {
        Log.e("ODS", "Error parseando content.xml con streaming", e)
    }
    return allSheets
}

