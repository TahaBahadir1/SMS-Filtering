package com.smsfiltering.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsfiltering.client.data.local.entity.SyncMetadataEntity

@Dao
interface SyncMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE `key` = :key LIMIT 1")
    suspend fun getMetadata(key: String): SyncMetadataEntity?

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    suspend fun deleteMetadata(key: String)
}
