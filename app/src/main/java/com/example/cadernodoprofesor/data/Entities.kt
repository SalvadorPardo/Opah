package com.example.cadernodoprofesor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cursos")
data class Curso(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val fechaInicio: String,
    val fechaFin: String,
    val estaActivo: Boolean = false
)

@Entity(tableName = "aulas")
data class Aula(
    @PrimaryKey val aulaId: String, // "HDDIJNP", "USMIJHAC", "AMBAS"
    val nombreAula: String
)

@Entity(
    tableName = "calendario",
    primaryKeys = ["fecha", "cursoId"] // Clave compuesta para evitar conflictos entre cursos
)
data class DiaCalendario(
    val fecha: String, // Formato "YYYY-MM-DD"
    val cursoId: Long,
    val esLectivo: Boolean, // true = Lectivo, false = Festivo
    val nombreFestivo: String? = null
)

@Entity(tableName = "gestion_eventos")
data class Evento(
    @PrimaryKey(autoGenerate = true) val eventoId: Long = 0,
    val fecha: String,
    val tipoEvento: String, // "CURSO", "REUNION", "SUCESO", "LICENCIA"
    val aulaId: String,
    val descripcion: String,
    val cursoId: Long = 0
)

@Entity(tableName = "preferencias")
data class Preferencias(
    @PrimaryKey val id: Int = 1,
    val nombreProfesor: String = "Salvador Martínez Pardo",
    val rolUsuario: String = "Aula Pediatría",
    val ciudadAula: String = "Vigo",
    val abalarboxServidor: String = "https://boxabalar.edu.xunta.gal/index.php/apps/dashboard/",
    val abalarboxUsuario: String = "smpardo",
    val abalarboxClave: String = "HmFLd-eStLQ-bNn9G-srXX7-9o4gn",
    // Espazo 1
    val espazo1Activo: Boolean = true,
    val espazo1Acronimo: String = "HDDIJNP",
    val espazo1Nombre: String = "Hospital de día Infanto Juvenil Nicolás Peña",
    // Espazo 2
    val espazo2Activo: Boolean = true,
    val espazo2Acronimo: String = "USMIJHAC",
    val espazo2Nombre: String = "Unidade de Saúde Mental Infanto Juvenil Hospital Álvaro Cunqueiro",
    val destinoInformes: String = "BOX", // "BOX", "LOCAL", "AMBOS"
    // Email Config
    val emailSmtpServidor: String = "smtp.edu.xunta.gal",
    val emailSmtpPuerto: Int = 587,
    val emailImapServidor: String = "imap.edu.xunta.gal",
    val emailImapPuerto: Int = 993,
    val emailDireccion: String = "",
    val emailClave: String = ""
)

@Entity(tableName = "alumnos")
data class Alumno(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nomeCompleto: String,
    val centroEstudos: String,
    val curso: String, // 1º, 2º...
    val nivel: String, // Primaria, ESO...
    val contactoReferencia: String, // Tutor/a, Orientador/a
    val contactoNome: String,
    val contactoEmail: String,
    val contactoTelefono: String,
    val dificultadesAprendizaxe: String,
    val medidasAtencion: String,
    val obxetivosXerais: String,
    val aulaId: String, // "HDDIJNP" o "USMIJHAC"
    val esActivo: Boolean = true,
    val tieneDocumentacion: Boolean = false,
    val fechaCreacion: String,
    val fechaIngreso: String = "",
    val fechaEval1: String = "",
    val fechaEval2: String = "",
    val fechaEval3: String = "",
    val contactoRecibeEntregas: Boolean = false,
    val entregaCanalBoxabalar: Boolean = true,
    val entregaCanalEmail: Boolean = false
)

@Entity(tableName = "materias_alumnos")
data class MateriaAlumno(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alumnoId: Long,
    val nombre: String,
    val profesor: String,
    val email: String,
    val objetivos: String
)

@Entity(tableName = "asistencia", primaryKeys = ["alumnoId", "fecha"])
data class Asistencia(
    val alumnoId: Long,
    val fecha: String,
    val aulaId: String
)

@Entity(tableName = "notas_alumnos")
data class NotaAlumno(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alumnoId: Long,
    val fecha: String,
    val tipo: String, // "INGRESO", "DIARIA"
    val contenido: String
)

@Entity(tableName = "registros_academicos", primaryKeys = ["alumnoId", "fecha"])
data class RegistroAcademico(
    val alumnoId: Long,
    val fecha: String, // YYYY-MM-DD
    val traballoDia: String = "",
    val paraProximaSesion: String = "",
    val alertaProximaSesion: Boolean = false,
    val observacionDia: String = "",
    val ocultarAlerta: Boolean = true
)

@Entity(tableName = "entregas_traballos")
data class EntregaTrabajo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alumnoId: Long,
    val materiaId: Long,
    val fecha: String,
    val descripcion: String,
    val archivoNombre: String,
    val urlNextcloud: String,
    val canal: String = "BOX" // "BOX", "EMAIL", "AMBOS"
)

@Entity(tableName = "movimientos_alumnos")
data class MovimientoAlumno(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alumnoId: Long,
    val tipo: String, // "ALTA", "BAIXA"
    val fecha: String
)

@Entity(tableName = "valoracions_informes", primaryKeys = ["alumnoId", "fecha", "item"])
data class ValoracionInforme(
    val alumnoId: Long,
    val fecha: String, // YYYY-MM-DD
    val item: String,
    val valoracion: String,
    val tipo: String // "OBXETIVO", "COMPETENCIA"
)

@Entity(tableName = "aula_diaria", primaryKeys = ["aulaId", "fecha"])
data class AulaDiaria(
    val aulaId: String,
    val fecha: String,
    val acompanantes: String = ""
)

data class AlertaPendiente(
    val alumnoId: Long,
    val fecha: String,
    val paraProximaSesion: String,
    val alumnoNombre: String,
    val alumnoAula: String,
    val estaActiva: Boolean
)
