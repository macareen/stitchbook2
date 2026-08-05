package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.CounterNote

@Entity(
    tableName = "counter_notes",
    foreignKeys = [
        ForeignKey(
            entity = CounterEntity::class,
            parentColumns = ["id"],
            childColumns = ["counter_id"],
            // A note only exists relative to the counter value it was
            // written against -- unlike a Counter's own optional project_id,
            // counter_id here is required, so a deleted counter takes its
            // notes with it rather than leaving them to point at nothing.
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("counter_id")]
)
data class CounterNoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "counter_id") val counterId: String,
    val value: Int,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

fun CounterNoteEntity.toDomain(): CounterNote {
    return CounterNote(
        id = id,
        counterId = counterId,
        value = value,
        note = note,
        createdAt = createdAt
    )
}

fun CounterNote.toEntity(): CounterNoteEntity {
    return CounterNoteEntity(
        id = id,
        counterId = counterId,
        value = value,
        note = note,
        createdAt = createdAt
    )
}
