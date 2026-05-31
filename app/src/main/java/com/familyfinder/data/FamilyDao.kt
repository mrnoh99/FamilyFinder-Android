package com.familyfinder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    @Query("SELECT * FROM family_members ORDER BY id ASC")
    fun getAllMembers(): Flow<List<FamilyMember>>

    @Insert
    suspend fun insert(member: FamilyMember): Long

    @Update
    suspend fun update(member: FamilyMember)

    @Delete
    suspend fun delete(member: FamilyMember)

    @Query("SELECT COUNT(*) FROM family_members")
    suspend fun getCount(): Int
}
