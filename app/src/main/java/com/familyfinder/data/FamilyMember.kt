package com.familyfinder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val relationship: String,
    val photoPath: String,
    val questionAudioPath: String,
    val correctAudioPath: String,
    val incorrectAudioPath: String
)
