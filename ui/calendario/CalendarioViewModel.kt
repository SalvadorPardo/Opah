package com.example.cadernodoprofesor.ui.calendario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cadernodoprofesor.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarioViewModel(private val repository: CalendarioDao) : ViewModel() {

    val cursos = repository.obtenerTodosLosCursos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val cursoActivo = repository.obtenerCursoActivo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val festivos = cursoActivo.flatMapLatest { curso ->
        if (curso != null) repository.obtenerCalendarioPorCurso(curso.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val eventos = cursoActivo.flatMapLatest { curso ->
        if (curso != null) repository.obtenerEventosPorCurso(curso.id)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun establecerCursoActivo(cursoId: Long) {
        viewModelScope.launch { repository.establecerCursoActivo(cursoId) }
    }

    fun insertarCurso(nombre: String, inicio: LocalDate, fin: LocalDate) {
        viewModelScope.launch {
            val curso = Curso(nombre = nombre, fechaInicio = inicio.toString(), fechaFin = fin.toString(), estaActivo = false)
            val id = repository.insertarCurso(curso)
            if (cursoActivo.value == null) establecerCursoActivo(id)
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

    fun eliminarFestivosPorNombre(nombre: String, cursoId: Long) {
        viewModelScope.launch {
            val aEliminar = festivos.value.filter { it.nombreFestivo == nombre }
            aEliminar.forEach { repository.eliminarDia(it) }
        }
    }

    fun insertarEvento(tipo: String, descripcion: String, fecha: LocalDate, aulaId: String) {
        viewModelScope.launch {
            cursoActivo.value?.let { curso ->
                repository.insertarEvento(Evento(fecha = fecha.toString(), tipoEvento = tipo, aulaId = aulaId, descripcion = descripcion, cursoId = curso.id))
            }
        }
    }

    fun eliminarEvento(evento: Evento) {
        viewModelScope.launch { repository.eliminarEvento(evento) }
    }

    fun eliminarCurso(curso: Curso) {
        viewModelScope.launch { repository.eliminarCurso(curso) }
    }
}
