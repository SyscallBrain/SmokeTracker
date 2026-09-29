package pt.tiago.cinzeiro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Esquema exportado para `app/schemas/` (versionado no git; base das futuras migrações). */
@Database(entities = [SmokeEventEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smokeEventDao(): SmokeEventDao

    companion object {
        const val NAME = "cinzeiro.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
