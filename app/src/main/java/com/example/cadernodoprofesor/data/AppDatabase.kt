package com.example.cadernodoprofesor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Curso::class, Aula::class, DiaCalendario::class, Evento::class, Preferencias::class, Alumno::class, Asistencia::class, NotaAlumno::class, MateriaAlumno::class, RegistroAcademico::class, EntregaTrabajo::class, MovimientoAlumno::class, AulaDiaria::class, ValoracionInforme::class], version = 21)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarioDao(): CalendarioDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calendario_profesor_db"
                )
                .addMigrations(MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alumnos ADD COLUMN contactoRecibeEntregas INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferencias ADD COLUMN emailSmtpServidor TEXT NOT NULL DEFAULT 'smtp.edu.xunta.gal'")
                db.execSQL("ALTER TABLE preferencias ADD COLUMN emailSmtpPuerto INTEGER NOT NULL DEFAULT 587")
                db.execSQL("ALTER TABLE preferencias ADD COLUMN emailImapServidor TEXT NOT NULL DEFAULT 'imap.edu.xunta.gal'")
                db.execSQL("ALTER TABLE preferencias ADD COLUMN emailImapPuerto INTEGER NOT NULL DEFAULT 993")
                db.execSQL("ALTER TABLE preferencias ADD COLUMN emailClave TEXT NOT NULL DEFAULT ''")
                
                db.execSQL("ALTER TABLE alumnos ADD COLUMN entregaCanalBoxabalar INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE alumnos ADD COLUMN entregaCanalEmail INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferencias ADD COLUMN emailDireccion TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entregas_traballos ADD COLUMN canal TEXT NOT NULL DEFAULT 'BOX'")
            }
        }
    }
}
