package pt.tiago.cinzeiro.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Table `smoke_event` (ADR decision 3): UTC instant + the entry's zone. */
@Entity(
    tableName = "smoke_event",
    indices = [Index(value = ["at_epoch_ms"])],
)
data class SmokeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "at_epoch_ms") val atEpochMs: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
)
