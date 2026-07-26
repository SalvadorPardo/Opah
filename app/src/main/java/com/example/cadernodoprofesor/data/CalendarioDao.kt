package com.example.cadernodoprofesor.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarioDao {
    // Cursos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCurso(curso: Curso): Long

    @Query("SELECT * FROM cursos ORDER BY id DESC")
    fun obtenerTodosLosCursos(): Flow<List<Curso>>

    @Query("SELECT * FROM cursos WHERE estaActivo = 1 LIMIT 1")
    fun obtenerCursoActivo(): Flow<Curso?>

    @Query("UPDATE cursos SET estaActivo = 0")
    suspend fun desactivarTodosLosCursos()

    @Query("UPDATE cursos SET estaActivo = 1 WHERE id = :cursoId")
    suspend fun activarCurso(cursoId: Long)

    @Transaction
    suspend fun establecerCursoActivo(cursoId: Long) {
        desactivarTodosLosCursos()
        activarCurso(cursoId)
    }

    @Delete
    suspend fun eliminarCurso(curso: Curso)

    // Calendario / Festivos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarDia(dia: DiaCalendario)

    @Delete
    suspend fun eliminarDia(dia: DiaCalendario)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarDias(dias: List<DiaCalendario>)

    @Query("SELECT * FROM calendario WHERE cursoId = :cursoId")
    fun obtenerCalendarioPorCurso(cursoId: Long): Flow<List<DiaCalendario>>

    @Query("SELECT * FROM calendario WHERE fecha = :fecha")
    suspend fun obtenerDiaEspecifico(fecha: String): DiaCalendario?

    // Eventos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarEvento(evento: Evento)

    @Query("SELECT * FROM gestion_eventos WHERE fecha = :fecha")
    fun obtenerEventosDelDia(fecha: String): Flow<List<Evento>>

    @Query("SELECT * FROM gestion_eventos WHERE cursoId = :cursoId")
    fun obtenerEventosPorCurso(cursoId: Long): Flow<List<Evento>>

    @Query("SELECT * FROM gestion_eventos WHERE aulaId = :aulaId OR aulaId = 'AMBAS'")
    fun obtenerEventosPorAula(aulaId: String): Flow<List<Evento>>

    @Query("SELECT * FROM gestion_eventos WHERE (aulaId = :aulaId OR aulaId = 'AMBAS' OR :aulaId = 'AMBAS') AND fecha BETWEEN :inicio AND :fin")
    fun obtenerEventosRango(inicio: String, fin: String, aulaId: String): Flow<List<Evento>>

    @Delete
    suspend fun eliminarEvento(evento: Evento)

    // Aulas (Auxiliar)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarAula(aula: Aula)

    @Query("SELECT * FROM aulas")
    suspend fun obtenerAulas(): List<Aula>

    // Preferencias
    @Query("SELECT * FROM preferencias WHERE id = 1")
    fun obtenerPreferencias(): Flow<Preferencias?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarPreferencias(preferencias: Preferencias)

    // Alumnos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarAlumno(alumno: Alumno): Long

    @Query("SELECT * FROM alumnos WHERE (aulaId = :aulaId OR :aulaId = 'AMBAS') AND esActivo = :soloActivos")
    fun obtenerAlumnosPorAula(aulaId: String, soloActivos: Boolean): Flow<List<Alumno>>

    @Query("SELECT * FROM alumnos WHERE (aulaId = :aulaId OR :aulaId = 'AMBAS')")
    fun obtenerTodosAlumnosPorAula(aulaId: String): Flow<List<Alumno>>

    @Query("UPDATE alumnos SET esActivo = :esActivo WHERE id = :alumnoId")
    suspend fun actualizarEstadoAlumno(alumnoId: Long, esActivo: Boolean)

    @Query("UPDATE alumnos SET esActivo = 0 WHERE esActivo = 1")
    suspend fun darDeBajaTodosLosAlumnos()

    @Delete
    suspend fun eliminarAlumno(alumno: Alumno)

    @Query("DELETE FROM alumnos")
    suspend fun eliminarTodosLosAlumnos()

    @Query("DELETE FROM materias_alumnos")
    suspend fun eliminarTodasLasMaterias()

    @Query("DELETE FROM notas_alumnos")
    suspend fun eliminarTodasLasNotas()

    @Query("DELETE FROM asistencia")
    suspend fun eliminarTodaLaAsistencia()

    // Asistencia
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun registrarAsistencia(asistencia: Asistencia)

    @Query("DELETE FROM asistencia WHERE alumnoId = :alumnoId AND fecha = :fecha")
    suspend fun eliminarAsistencia(alumnoId: Long, fecha: String)

    @Query("SELECT * FROM asistencia WHERE fecha = :fecha AND aulaId = :aulaId")
    fun obtenerAsistenciaDelDia(fecha: String, aulaId: String): Flow<List<Asistencia>>

    @Query("SELECT * FROM asistencia WHERE (aulaId = :aulaId OR :aulaId = 'AMBAS') AND fecha BETWEEN :inicio AND :fin")
    fun obtenerAsistenciaRango(inicio: String, fin: String, aulaId: String): Flow<List<Asistencia>>

    // Notas Alumnos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarNotaAlumno(nota: NotaAlumno)

    @Query("SELECT * FROM notas_alumnos WHERE alumnoId = :alumnoId ORDER BY fecha DESC, id DESC")
    fun obtenerNotasAlumno(alumnoId: Long): Flow<List<NotaAlumno>>

    @Delete
    suspend fun eliminarNotaAlumno(nota: NotaAlumno)

    // Materias Alumnos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarMateriaAlumno(materia: MateriaAlumno)

    @Query("SELECT * FROM materias_alumnos WHERE alumnoId = :alumnoId")
    fun obtenerMateriasAlumno(alumnoId: Long): Flow<List<MateriaAlumno>>

    @Delete
    suspend fun eliminarMateriaAlumno(materia: MateriaAlumno)

    // Entregas de Traballos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarEntregaTrabajo(entrega: EntregaTrabajo)

    @Query("SELECT * FROM entregas_traballos WHERE alumnoId = :alumnoId AND materiaId = :materiaId ORDER BY fecha DESC")
    fun obtenerEntregasPorMateria(alumnoId: Long, materiaId: Long): Flow<List<EntregaTrabajo>>

    @Query("SELECT * FROM entregas_traballos WHERE alumnoId = :alumnoId")
    fun obtenerTodasEntregasAlumno(alumnoId: Long): Flow<List<EntregaTrabajo>>

    @Delete
    suspend fun eliminarEntregaTrabajo(entrega: EntregaTrabajo)

    // Movimientos Alumnos (Altas/Baixas)
    @Insert
    suspend fun insertarMovimientoAlumno(movimiento: MovimientoAlumno)

    @Query("SELECT * FROM movimientos_alumnos WHERE alumnoId = :alumnoId ORDER BY fecha DESC")
    fun obtenerMovimientosAlumno(alumnoId: Long): Flow<List<MovimientoAlumno>>

    @Query("""
        SELECT m.* FROM movimientos_alumnos m
        JOIN alumnos a ON m.alumnoId = a.id
        WHERE (a.aulaId = :aulaId OR :aulaId = 'AMBAS')
        ORDER BY m.fecha ASC
    """)
    fun obtenerMovimientosPorAula(aulaId: String): Flow<List<MovimientoAlumno>>

    @Query("DELETE FROM movimientos_alumnos")
    suspend fun eliminarTodosLosMovimientos()

    // Registros Académicos Diarios
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarRegistroAcademico(registro: RegistroAcademico)

    @Query("SELECT * FROM registros_academicos WHERE alumnoId = :alumnoId AND fecha = :fecha")
    fun obtenerRegistroAcademico(alumnoId: Long, fecha: String): Flow<RegistroAcademico?>

    @Query("SELECT * FROM registros_academicos WHERE alumnoId = :alumnoId AND fecha < :fecha ORDER BY fecha DESC LIMIT 1")
    suspend fun obtenerUltimoRegistroAnterior(alumnoId: Long, fecha: String): RegistroAcademico?

    @Query("SELECT * FROM registros_academicos WHERE alumnoId = :alumnoId ORDER BY fecha DESC")
    fun obtenerHistorialAcademico(alumnoId: Long): Flow<List<RegistroAcademico>>
    
    @Query("UPDATE registros_academicos SET alertaProximaSesion = :activa WHERE alumnoId = :alumnoId AND fecha = :fecha")
    suspend fun actualizarEstadoAlerta(alumnoId: Long, fecha: String, activa: Boolean)

    @Query("UPDATE registros_academicos SET ocultarAlerta = 1 WHERE alumnoId = :alumnoId AND fecha = :fecha")
    suspend fun ocultarAlerta(alumnoId: Long, fecha: String)

    @Query("""
        SELECT r.alumnoId, r.fecha, r.paraProximaSesion, a.nomeCompleto as alumnoNombre, a.aulaId as alumnoAula, r.alertaProximaSesion as estaActiva
        FROM registros_academicos r 
        JOIN alumnos a ON r.alumnoId = a.id 
        WHERE r.paraProximaSesion != '' AND r.ocultarAlerta = 0
        ORDER BY r.alertaProximaSesion DESC, r.fecha DESC
    """)
    fun obtenerAlertasPendientes(): Flow<List<AlertaPendiente>>

    @Query("DELETE FROM registros_academicos")
    suspend fun eliminarTodosLosRegistrosAcademicos()

    // Valoracions Informes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarValoracionInforme(valoracion: ValoracionInforme)

    @Query("SELECT * FROM valoracions_informes WHERE alumnoId = :alumnoId AND fecha = :fecha")
    fun obtenerValoracionsInforme(alumnoId: Long, fecha: String): Flow<List<ValoracionInforme>>

    // Aula Diaria (Acompañantes, etc)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarAulaDiaria(aulaDiaria: AulaDiaria)

    @Query("SELECT * FROM aula_diaria WHERE aulaId = :aulaId AND fecha = :fecha")
    fun obtenerAulaDiaria(aulaId: String, fecha: String): Flow<AulaDiaria?>
}
