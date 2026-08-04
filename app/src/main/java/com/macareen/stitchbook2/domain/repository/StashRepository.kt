package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.StashItem
import kotlinx.coroutines.flow.Flow

interface StashRepository {
    fun observeStashItems(): Flow<List<StashItem>>

    fun observeStashItem(id: String): Flow<StashItem?>

    suspend fun saveStashItem(item: StashItem)

    suspend fun deleteStashItem(item: StashItem)
}
