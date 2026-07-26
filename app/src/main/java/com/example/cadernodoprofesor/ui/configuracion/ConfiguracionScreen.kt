package com.example.cadernodoprofesor.ui.configuracion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.cadernodoprofesor.data.Preferencias
import com.example.cadernodoprofesor.ui.calendario.CalendarioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(viewModel: CalendarioViewModel) {
    val prefs by viewModel.preferencias.collectAsState()
    val pDefault = Preferencias()
    
    // Estados locales para los campos editables
    var nombreProfesor by remember(prefs) { 
        mutableStateOf(if (prefs?.nombreProfesor.isNullOrBlank()) pDefault.nombreProfesor else prefs!!.nombreProfesor) 
    }
    var rolUsuario by remember(prefs) { 
        mutableStateOf(if (prefs?.rolUsuario.isNullOrBlank()) pDefault.rolUsuario else prefs!!.rolUsuario) 
    }
    var ciudadAula by remember(prefs) { 
        mutableStateOf(if (prefs?.ciudadAula.isNullOrBlank()) pDefault.ciudadAula else prefs!!.ciudadAula) 
    }
    var abalarboxServidor by remember(prefs) { 
        mutableStateOf(if (prefs?.abalarboxServidor.isNullOrBlank()) pDefault.abalarboxServidor else prefs!!.abalarboxServidor) 
    }
    var abalarboxUsuario by remember(prefs) { 
        mutableStateOf(if (prefs?.abalarboxUsuario.isNullOrBlank()) pDefault.abalarboxUsuario else prefs!!.abalarboxUsuario) 
    }
    var abalarboxClave by remember(prefs) { 
        mutableStateOf(if (prefs?.abalarboxClave.isNullOrBlank()) pDefault.abalarboxClave else prefs!!.abalarboxClave) 
    }

    var emailSmtpServidor by remember(prefs) { 
        mutableStateOf(if (prefs?.emailSmtpServidor.isNullOrBlank()) pDefault.emailSmtpServidor else prefs!!.emailSmtpServidor) 
    }
    var emailSmtpPuerto by remember(prefs) { 
        mutableIntStateOf(prefs?.emailSmtpPuerto ?: pDefault.emailSmtpPuerto) 
    }
    var emailImapServidor by remember(prefs) { 
        mutableStateOf(if (prefs?.emailImapServidor.isNullOrBlank()) pDefault.emailImapServidor else prefs!!.emailImapServidor) 
    }
    var emailImapPuerto by remember(prefs) { 
        mutableIntStateOf(prefs?.emailImapPuerto ?: pDefault.emailImapPuerto) 
    }
    var emailDireccion by remember(prefs) { 
        mutableStateOf(prefs?.emailDireccion ?: "") 
    }
    var emailClave by remember(prefs) { 
        mutableStateOf(prefs?.emailClave ?: "") 
    }
    
    var espazo1Activo by remember(prefs) { mutableStateOf(prefs?.espazo1Activo ?: true) }
    var espazo1Acronimo by remember(prefs) { mutableStateOf(prefs?.espazo1Acronimo ?: "HDDIJNP") }
    var espazo1Nombre by remember(prefs) { mutableStateOf(prefs?.espazo1Nombre ?: "Hospital de día Infanto Juvenil Nicolás Peña") }
    
    var espazo2Activo by remember(prefs) { mutableStateOf(prefs?.espazo2Activo ?: true) }
    var espazo2Acronimo by remember(prefs) { mutableStateOf(prefs?.espazo2Acronimo ?: "USMIJHAC") }
    var espazo2Nombre by remember(prefs) { mutableStateOf(prefs?.espazo2Nombre ?: "Unidade de Saúde Mental Infanto Juvenil Hospital Álvaro Cunqueiro") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomAppBar {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Button(
                        onClick = {
                            val nuevasPrefs = Preferencias(
                                id = prefs?.id ?: 1,
                                nombreProfesor = nombreProfesor.trim(),
                                rolUsuario = rolUsuario,
                                ciudadAula = ciudadAula,
                                abalarboxServidor = abalarboxServidor.trim(),
                                abalarboxUsuario = abalarboxUsuario.trim(),
                                abalarboxClave = abalarboxClave.trim(),
                                espazo1Activo = espazo1Activo,
                                espazo1Acronimo = espazo1Acronimo.trim(),
                                espazo1Nombre = espazo1Nombre.trim(),
                                espazo2Activo = espazo2Activo,
                                espazo2Acronimo = espazo2Acronimo.trim(),
                                espazo2Nombre = espazo2Nombre.trim(),
                                destinoInformes = prefs?.destinoInformes ?: "BOX",
                                emailSmtpServidor = emailSmtpServidor.trim(),
                                emailSmtpPuerto = emailSmtpPuerto,
                                emailImapServidor = emailImapServidor.trim(),
                                emailImapPuerto = emailImapPuerto,
                                emailDireccion = emailDireccion.trim(),
                                emailClave = emailClave
                            )
                            viewModel.actualizarPreferencias(nuevasPrefs)
                            scope.launch {
                                snackbarHostState.showSnackbar("Configuración gardada correctamente")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Gardar cambios")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Datos do profesor
            Text("DATOS DO PROFESOR/A", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            OutlinedTextField(
                value = nombreProfesor,
                onValueChange = { nombreProfesor = it },
                label = { Text("Nome do profesor/a") },
                modifier = Modifier.fillMaxWidth()
            )

            val roles = listOf(
                "Unidad de Saúde Mental", "Aula Pediatría", "Habitacións", "Coordinación",
                "Inspección", "Contacto centros", "Atención domiciliaria 1", "Atención domiciliaria 2",
                "Atención domiciliaria 3", "Atención domiciliaria 4", "Atención domiciliaria 5",
                "Atención domiciliaria 6", "Atención domiciliaria 7", "Atención domiciliaria 8"
            )
            var expandedRol by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expandedRol,
                onExpandedChange = { expandedRol = !expandedRol }
            ) {
                OutlinedTextField(
                    value = rolUsuario,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Rol / Función") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRol) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedRol,
                    onDismissRequest = { expandedRol = false }
                ) {
                    roles.forEach { rol ->
                        DropdownMenuItem(
                            text = { Text(rol) },
                            onClick = {
                                rolUsuario = rol
                                expandedRol = false
                            }
                        )
                    }
                }
            }

            val ciudades = listOf("A Coruña", "Ferrol", "Lugo", "Ourense", "Pontevedra", "Santiago de Compostela", "Vigo")
            var expanded by remember { mutableStateOf(false) }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = ciudadAula,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Aula hospitalaria de") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ciudades.forEach { ciudad ->
                        DropdownMenuItem(
                            text = { Text(ciudad) },
                            onClick = {
                                ciudadAula = ciudad
                                expanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // 2. Conta de Abalarbox
            Text("CONTA DE ABALARBOX", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            OutlinedTextField(
                value = abalarboxServidor,
                onValueChange = { abalarboxServidor = it },
                label = { Text("Servidor de Abalarbox") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = abalarboxUsuario,
                onValueChange = { abalarboxUsuario = it },
                label = { Text("Usuario") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "BoxAbalar / Perfil / Axustes / Seguranza/ Crear un novo contrasinal de App.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = abalarboxClave,
                    onValueChange = { abalarboxClave = it },
                    label = { Text("Clave") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
            }

            HorizontalDivider()

            // 3. Espazos de traballo
            Text("ESPAZOS DE TRABALLO", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            // Espazo 1
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = espazo1Activo, onCheckedChange = { espazo1Activo = it })
                    Text("Espazo 1: Activo", fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = espazo1Acronimo,
                    onValueChange = { espazo1Acronimo = it },
                    label = { Text("Acrónimo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = espazo1Nombre,
                    onValueChange = { espazo1Nombre = it },
                    label = { Text("Nome completo") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Espazo 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = espazo2Activo, onCheckedChange = { espazo2Activo = it })
                    Text("Espazo 2: Activo", fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = espazo2Acronimo,
                    onValueChange = { espazo2Acronimo = it },
                    label = { Text("Acrónimo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = espazo2Nombre,
                    onValueChange = { espazo2Nombre = it },
                    label = { Text("Nome completo") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            // 4. Configuración do Email
            Text("CONFIGURACIÓN DO EMAIL", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emailSmtpServidor,
                        onValueChange = { emailSmtpServidor = it },
                        label = { Text("Servidor SMTP") },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = emailSmtpPuerto.toString(),
                        onValueChange = { emailSmtpPuerto = it.toIntOrNull() ?: emailSmtpPuerto },
                        label = { Text("Porto") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emailImapServidor,
                        onValueChange = { emailImapServidor = it },
                        label = { Text("Servidor IMAP") },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = emailImapPuerto.toString(),
                        onValueChange = { emailImapPuerto = it.toIntOrNull() ?: emailImapPuerto },
                        label = { Text("Porto") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = emailDireccion,
                    onValueChange = { emailDireccion = it },
                    label = { Text("Enderezo de Email completo") },
                    placeholder = { Text("usuario@edu.xunta.gal") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = emailClave,
                    onValueChange = { emailClave = it },
                    label = { Text("Contrasinal Email") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
