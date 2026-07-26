package com.example.cadernodoprofesor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cadernodoprofesor.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AutomationQATest {
    private lateinit var db: AppDatabase
    private lateinit var dao: CalendarioDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.calendarioDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testAttendanceRangeReporting() = runBlocking {
        // Setup data
        val alumnoId = 1L
        val aulaId = "HDDIJNP"
        val fecha1 = "2024-01-01"
        val fecha2 = "2024-01-15"
        val fechaFuera = "2024-02-01"

        dao.registrarAsistencia(Asistencia(alumnoId, fecha1, aulaId))
        dao.registrarAsistencia(Asistencia(alumnoId, fecha2, aulaId))
        dao.registrarAsistencia(Asistencia(alumnoId, fechaFuera, aulaId))

        // Query range
        val result = dao.obtenerAsistenciaRango("2024-01-01", "2024-01-31", aulaId).first()

        assertEquals(2, result.size)
        assertTrue(result.any { it.fecha == fecha1 })
        assertTrue(result.any { it.fecha == fecha2 })
    }

    @Test
    fun testEventRangeReporting() = runBlocking {
        val aulaId = "HDDIJNP"
        dao.insertarEvento(Evento(1, "2024-05-10", "REUNION", aulaId, "Test Event 1", 1))
        dao.insertarEvento(Evento(2, "2024-05-20", "LICENCIA", aulaId, "Test Event 2", 1))
        dao.insertarEvento(Evento(3, "2024-06-01", "FESTIVO", aulaId, "Out of range", 1))

        val result = dao.obtenerEventosRango("2024-05-01", "2024-05-31", aulaId).first()

        assertEquals(2, result.size)
        assertEquals("Test Event 1", result[0].descripcion)
    }

    @Test
    fun testCentrosDistinctLogic() = runBlocking {
        val aulaId = "USMIJHAC"
        val alumno1 = Alumno(1, "Alumno A", "Centro 1", "1", "Primaria", "", "", "", "", "", "", "", aulaId, true, false, "2024-01-01", "", "", "")
        val alumno2 = Alumno(2, "Alumno B", "Centro 2", "1", "Primaria", "", "", "", "", "", "", "", aulaId, true, false, "2024-01-01", "", "", "")
        val alumno3 = Alumno(3, "Alumno C", "Centro 1", "1", "Primaria", "", "", "", "", "", "", "", aulaId, true, false, "2024-01-01", "", "", "")
        
        dao.insertarAlumno(alumno1)
        dao.insertarAlumno(alumno2)
        dao.insertarAlumno(alumno3)
        
        dao.registrarAsistencia(Asistencia(1, "2024-03-01", aulaId))
        dao.registrarAsistencia(Asistencia(2, "2024-03-02", aulaId))
        dao.registrarAsistencia(Asistencia(3, "2024-03-03", aulaId))

        val asistencias = dao.obtenerAsistenciaRango("2024-03-01", "2024-03-31", aulaId).first()
        val alumnos = dao.obtenerAlumnosPorAula(aulaId, true).first()
        
        val idsAlumnosPeriodo = asistencias.map { it.alumnoId }.toSet()
        val centros = alumnos
            .filter { it.id in idsAlumnosPeriodo && it.centroEstudos.isNotBlank() }
            .map { it.centroEstudos }
            .distinct()
            .sorted()

        assertEquals(2, centros.size)
        assertEquals(listOf("Centro 1", "Centro 2"), centros)
    }
}
