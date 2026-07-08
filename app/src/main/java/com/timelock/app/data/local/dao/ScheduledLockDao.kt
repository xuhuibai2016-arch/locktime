package com.timelock.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.timelock.app.data.local.entity.ScheduledLockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledLockDao {

    @Query("SELECT * FROM scheduled_lock_config WHERE id = 1")
    suspend fun get(): ScheduledLockEntity?

    @Query("SELECT * FROM scheduled_lock_config WHERE id = 1")
    fun observe(): Flow<ScheduledLockEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(config: ScheduledLockEntity)
}
