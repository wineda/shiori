package com.wineda.shiori.data.repository

import com.wineda.shiori.data.local.entity.JournalEntity
import com.wineda.shiori.data.local.entity.MemoEntity
import com.wineda.shiori.domain.model.Journal
import com.wineda.shiori.domain.model.Memo
import com.wineda.shiori.domain.model.MemoTag
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

internal fun JournalEntity.toDomain() = Journal(
    date = LocalDate.parse(date),
    good = good,
    hard = hard,
    insight = insight,
    tomorrow = tomorrow,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

internal fun Journal.toEntity(existingCreatedAt: Long? = null) = JournalEntity(
    date = date.toString(),
    good = good,
    hard = hard,
    insight = insight,
    tomorrow = tomorrow,
    createdAt = existingCreatedAt ?: createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)

internal fun MemoEntity.toDomain() = Memo(
    id = id,
    date = LocalDate.parse(date),
    timestamp = Instant.fromEpochMilliseconds(timestamp),
    text = text,
    tag = MemoTag.fromKey(tag),
)

internal fun Memo.toEntity() = MemoEntity(
    id = id,
    date = date.toString(),
    timestamp = timestamp.toEpochMilliseconds(),
    text = text,
    tag = tag?.key,
)
