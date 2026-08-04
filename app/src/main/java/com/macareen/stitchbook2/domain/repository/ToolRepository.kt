package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import kotlinx.coroutines.flow.Flow

interface ToolRepository {
    fun observeToolItems(): Flow<List<ToolItem>>

    fun observeToolItem(id: String): Flow<ToolItem?>

    fun observeToolItemsBySet(setId: String): Flow<List<ToolItem>>

    suspend fun saveToolItem(item: ToolItem)

    suspend fun deleteToolItem(item: ToolItem)

    fun observeToolSets(): Flow<List<ToolSet>>

    fun observeToolSet(id: String): Flow<ToolSet?>

    suspend fun saveToolSet(set: ToolSet)

    suspend fun deleteToolSet(set: ToolSet)
}
