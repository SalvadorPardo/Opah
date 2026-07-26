package com.example.cadernodoprofesor.data

import android.content.Context

class CalendarioRepository(private val calendarioDao: CalendarioDao) {

    suspend fun guardarEvento(evento: Evento): Boolean {
        // 1. Buscamos el día en el calendario base
        val dia = calendarioDao.obtenerDiaEspecifico(evento.fecha)
        
        // 2. Si el día es lectivo, permitimos guardar. Si es festivo, lo bloqueamos.
        // Asumimos que si no existe en la tabla calendario, por defecto no es lectivo o hay que definirlo.
        return if (dia != null && dia.esLectivo) {
            calendarioDao.insertarEvento(evento)
            true // Guardado con éxito
        } else {
            false // Error: Día festivo o no configurado
        }
    }
}
