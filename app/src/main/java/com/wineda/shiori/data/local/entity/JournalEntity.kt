package com.wineda.shiori.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journals")
data class JournalEntity(
    @PrimaryKey val date: String,
    val good: String = "",
    val hard: String = "",
    val insight: String = "",
    val tomorrow: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)
