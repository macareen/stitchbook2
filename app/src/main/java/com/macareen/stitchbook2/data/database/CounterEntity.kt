package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.Counter

@Entity(
    tableName = "counters",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            // A project's counters are part of that project's own record,
            // the same relationship Guides already have to their Project
            // (see GuideEntity) -- deleting the project deletes them too. A
            // null project_id (a standalone counter) is simply never
            // subject to this cascade.
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CounterEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_counter_id"],
            // A link is a pointer to another counter, not ownership of it --
            // the same relationship tool_items has to tool_sets via set_id
            // (see ToolItemEntity). Deleting the linked-to counter should
            // just clear this counter's link, not delete this counter.
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("project_id"), Index("linked_counter_id")]
)
data class CounterEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String?,
    val name: String,
    @ColumnInfo(name = "unit_label") val unitLabel: String,
    @ColumnInfo(name = "current_value") val currentValue: Int,
    val goal: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "linked_counter_id") val linkedCounterId: String?,
    @ColumnInfo(name = "link_increment_interval") val linkIncrementInterval: Int?,
    @ColumnInfo(name = "link_increment_amount") val linkIncrementAmount: Int?,
    @ColumnInfo(name = "auto_reset_on_goal") val autoResetOnGoal: Boolean
)

fun CounterEntity.toDomain(): Counter {
    return Counter(
        id = id,
        projectId = projectId,
        name = name,
        unitLabel = unitLabel,
        currentValue = currentValue,
        goal = goal,
        createdAt = createdAt,
        updatedAt = updatedAt,
        linkedCounterId = linkedCounterId,
        linkIncrementInterval = linkIncrementInterval,
        linkIncrementAmount = linkIncrementAmount,
        autoResetOnGoal = autoResetOnGoal
    )
}

fun Counter.toEntity(): CounterEntity {
    return CounterEntity(
        id = id,
        projectId = projectId,
        name = name,
        unitLabel = unitLabel,
        currentValue = currentValue,
        goal = goal,
        createdAt = createdAt,
        updatedAt = updatedAt,
        linkedCounterId = linkedCounterId,
        linkIncrementInterval = linkIncrementInterval,
        linkIncrementAmount = linkIncrementAmount,
        autoResetOnGoal = autoResetOnGoal
    )
}
