# 調査レポート: Home画面「昨日からのバトン」が表示されない

## 1. サマリ
- **最有力原因**: `JournalDao.getTomorrowBaton()` が `isBackfilled = 0` を要求しているため、昨日のジャーナルがバックフィル扱いで保存されている場合は `tomorrow` が存在しても Home のバトン取得結果が `null` になる。
- **影響範囲**: Home画面のバトン表示、および通常Write画面上部の「昨日からのバトン」表示にも波及。Archive/ArchiveDetail の表示自体は `tomorrow` を参照するため、DB上の値確認は可能。
- **再現確実性**: 条件付き（「昨日」の記録が `isBackfilled = true` で保存されている場合は高確率で再現）。
- **修正の難易度感**: 小（DAO条件の見直しが主。ただし `isBackfilled` の意味づけ・既存データ扱いの仕様確認は必要）。

## 2. データフロー追跡
保存側:
`WriteScreen` → `WriteViewModel.onFieldChanged` → `saveDebouncer` → `SaveJournalUseCase` → `JournalRepository.save` → `JournalDao.upsert`

- `WriteScreen` の各入力欄は `viewModel.onFieldChanged(...)` を呼び、`tomorrow` 欄も同じ経路で更新される（`app/src/main/java/com/wineda/shiori/ui/write/WriteScreen.kt:80-90`）。
- `WriteViewModel.onFieldChanged()` は `updated` を作り、`saveDebouncer.tryEmit(updated)` に流す（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:82-95`）。
- `saveDebouncer` は 1.5 秒 debounce 後に `saveJournal(journal.markBackfillIfNeeded())` を呼ぶ（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:76-79`）。
- `SaveJournalUseCase` は `JournalRepository.save()` に委譲する（`app/src/main/java/com/wineda/shiori/domain/usecase/SaveJournalUseCase.kt:7-8`）。
- `JournalRepository.save()` は `isBackfilled` を算定して `journalDao.upsert(...)` する（`app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:32-39`）。
- `JournalDao.upsert()` は `OnConflictStrategy.REPLACE` で `journals` に保存する（`app/src/main/java/com/wineda/shiori/data/local/dao/JournalDao.kt:33-34`）。

表示側:
`HomeScreen` ← `HomeViewModel.uiState` ← `combine(..., baton, ...)` ← `GetYesterdayBatonUseCase` ← `JournalRepository.getTomorrowBaton` ← `JournalDao.getTomorrowBaton`

- `HomeScreen` は `viewModel.uiState.collectAsState()` で状態を購読し、`BatonCard(state.baton)` を描画する（`app/src/main/java/com/wineda/shiori/ui/home/HomeScreen.kt:56-57`, `app/src/main/java/com/wineda/shiori/ui/home/HomeScreen.kt:107-108`）。
- `HomeViewModel` は `baton = MutableStateFlow<String?>(null)` を持ち、`uiState` の `baton` に合成する（`app/src/main/java/com/wineda/shiori/ui/home/HomeViewModel.kt:37-49`）。
- `HomeViewModel.init` で `baton.value = getYesterdayBaton(today)` が非同期に実行される（`app/src/main/java/com/wineda/shiori/ui/home/HomeViewModel.kt:51-52`）。
- `GetYesterdayBatonUseCase` は `today.minus(1, DateTimeUnit.DAY)` を作り、Repository からその日の `tomorrow` を取得して空白だけの文字列を除外する（`app/src/main/java/com/wineda/shiori/domain/usecase/GetYesterdayBatonUseCase.kt:10-12`）。
- `JournalRepository.getTomorrowBaton()` は DAO にそのまま委譲する（`app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:30`）。
- `JournalDao.getTomorrowBaton()` は `date = :date AND isBackfilled = 0` の行だけから `tomorrow` を返す（`app/src/main/java/com/wineda/shiori/data/local/dao/JournalDao.kt:27-28`）。

## 3. 仮説検証結果
| # | 仮説 | 判定 | 根拠(ファイル:行番号 + 該当コード) |
|---|---|---|---|
| A | DAO の `AND isBackfilled = 0` で弾かれている | ✅ 該当（最有力） | `JournalDao.getTomorrowBaton` は `@Query("SELECT tomorrow FROM journals WHERE date = :date AND isBackfilled = 0")` であり、`isBackfilled = true` の昨日レコードは `tomorrow` が入っていても取得対象外になる（`app/src/main/java/com/wineda/shiori/data/local/dao/JournalDao.kt:27-28`）。保存側では過去日ルートなら `isBackfillMode = targetDate < today` で `true` になり（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:45-57`）、`onFieldChanged()` と保存直前にも `isBackfillMode` OR 既存値 で維持される（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:82-102`）。 |
| B | `save()` の `isBackfilled` 算定の境界バグ | ✅ 条件付きで該当 | `JournalRepository.save()` は `createdAt` からローカル日付 `createdDate` を復元し、`journal.date != createdDate` OR `journal.isBackfilled` で保存値を決める（`app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:32-38`）。そのため、日付境界をまたいで `journal.date` と `createdDate` がずれた場合、または上流で一度 `journal.isBackfilled = true` になった場合は `true` が残る。通常の「今日を書く」直後の新規作成では `targetDate = today` かつ `emptyJournal.isBackfilled = false` なので該当しない（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:45-57`）。 |
| C | `WriteViewModel.isBackfillMode` の初期判定タイミング | 保留（境界条件ではあり得るが、主因証拠はAより弱い） | `today` は ViewModel 生成時に固定され、`targetDate` も `SavedStateHandle` の `date` またはその時点の `today` で決まる（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:45-47`）。同一 ViewModel 内では深夜跨ぎ後に `isBackfillMode` を再計算しないため、開いたまま保存するケースだけでは逆に `isBackfillMode=false` のままになりやすい。ただし、翌日以降に `write/{date}` で旧日付を開き直すと過去日扱いになる。 |
| D | `fallbackToDestructiveMigration` によるデータ消失 | 保留（別原因としては成立、今回症状の説明力は限定的） | DBは `version = 2`（`app/src/main/java/com/wineda/shiori/data/local/ShioriDatabase.kt:10`）で、生成時に `fallbackToDestructiveMigration(true)` を指定している（`app/src/main/java/com/wineda/shiori/di/DatabaseModule.kt:20-24`）。移行未定義のアップグレード時にデータが消えれば `tomorrow` 自体が存在しないため同じ表示になるが、Archiveで昨日の `tomorrow` が確認できる状況とは両立しにくい。 |
| E | `getYesterdayBaton` の `takeIf { it.isNotBlank() }` 動作 | ❌ 通常の文字列では非該当 | `GetYesterdayBatonUseCase` は DAO結果に `?.takeIf { it.isNotBlank() }` を適用する（`app/src/main/java/com/wineda/shiori/domain/usecase/GetYesterdayBatonUseCase.kt:10-12`）。空文字・空白だけなら意図通り非表示だが、「文字列が入っている」通常ケースを弾くロジックではない。 |
| F | タイムゾーン / 日付計算 | 保留（保存時 `createdAt` 判定には関与、表示側単体では弱い） | Home 側は `Clock.System.todayIn(TimeZone.currentSystemDefault())` で今日を決め（`app/src/main/java/com/wineda/shiori/ui/home/HomeViewModel.kt:37`）、UseCase 側も渡された `today` から `today.minus(1, DateTimeUnit.DAY)` を作る（`app/src/main/java/com/wineda/shiori/domain/usecase/GetYesterdayBatonUseCase.kt:10-12`）。保存側では `createdAt` を `TimeZone.currentSystemDefault()` で `createdDate` に変換して `isBackfilled` 判定に使うため、日付境界やタイムゾーン変更はB経由で影響し得る（`app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:35-37`）。 |
| G | `BatonCard` 描画 / StateFlow タイミング | ❌ 主因ではない | `BatonCard` は `text != null` なら `「$it」`、`null` ならフォールバックを出すだけで、非null文字列を追加で弾かない（`app/src/main/java/com/wineda/shiori/ui/components/ShioriScaffoldBits.kt:121-139`）。`HomeViewModel` の初期値は `HomeUiState(today)`/`baton=null` だが、`baton` は `MutableStateFlow` で保持され、`combine(...).stateIn(...)` に合成されるため、購読中であれば更新は反映される構造になっている（`app/src/main/java/com/wineda/shiori/ui/home/HomeViewModel.kt:37-52`）。 |

## 4. 最有力原因の詳細
### 該当箇所
```kotlin
// app/src/main/java/com/wineda/shiori/data/local/dao/JournalDao.kt:27-28
@Query("SELECT tomorrow FROM journals WHERE date = :date AND isBackfilled = 0")
suspend fun getTomorrowBaton(date: String): String?
```

補助的に、`isBackfilled` が `true` になる保存経路は以下。

```kotlin
// app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:45-57
private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
private val targetDate = savedStateHandle.get<String>("date")?.let(LocalDate::parse) ?: today
private val isBackfillMode = targetDate < today
private val emptyJournal = Journal(
    date = targetDate,
    good = "",
    hard = "",
    insight = "",
    tomorrow = "",
    createdAt = Clock.System.now(),
    updatedAt = Clock.System.now(),
    isBackfilled = isBackfillMode,
)
```

```kotlin
// app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:82-102
fun onFieldChanged(good: String? = null, hard: String? = null, insight: String? = null, tomorrow: String? = null) {
    val updated = _uiState.value.journal.let {
        it.copy(
            good = good ?: it.good,
            hard = hard ?: it.hard,
            insight = insight ?: it.insight,
            tomorrow = tomorrow ?: it.tomorrow,
            updatedAt = Clock.System.now(),
            isBackfilled = isBackfillMode || it.isBackfilled,
        )
    }
    _uiState.update { it.copy(journal = updated, saved = false) }
    saveDebouncer.tryEmit(updated)
}

fun saveNow() = viewModelScope.launch {
    saveJournal(_uiState.value.journal.markBackfillIfNeeded())
    _uiState.update { it.copy(saved = true) }
}

private fun Journal.markBackfillIfNeeded(): Journal = copy(isBackfilled = isBackfillMode || isBackfilled)
```

```kotlin
// app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:32-39
suspend fun save(journal: Journal) {
    val existing = journalDao.getByDate(journal.date.toString())
    val now = Clock.System.now()
    val createdAt = existing?.createdAt ?: journal.createdAt.toEpochMilliseconds()
    val createdDate = kotlinx.datetime.Instant.fromEpochMilliseconds(createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
    val isBackfilled = journal.date != createdDate || journal.isBackfilled
    journalDao.upsert(journal.copy(updatedAt = now, isBackfilled = isBackfilled).toEntity(createdAt))
}
```

### なぜこれが原因か
Home の「昨日からのバトン」は、「昨日の日付」から `journals.tomorrow` を読む機能である。しかし実際の DAO は `date = :date` だけでなく `isBackfilled = 0` も要求しているため、昨日レコードがバックフィル扱いなら `tomorrow` に文字列が入っていても SQL の検索対象から外れる。結果として `JournalRepository.getTomorrowBaton()` は `null` を返し、`GetYesterdayBatonUseCase` も `null` を返し、`BatonCard` は「まだバトンはありません」を表示する。

`isBackfilled` は表示用メタ情報として Archive の行に `BACKFILL` ラベルを付ける用途にも使われているが、バトンの情報価値そのものを無効化する根拠はコード上では確認できない。むしろ `ArchiveDetailScreen` は `journal.tomorrow` をそのまま「明日へのバトン」として表示するため、Archiveで見える `tomorrow` がHomeでは見えない、というユーザー報告と整合する（`app/src/main/java/com/wineda/shiori/ui/archive/ArchiveDetailScreen.kt:34-39`）。バックフィルであっても「昨日の記録に書かれた明日へのバトン」は、Homeの「昨日からのバトン」として有効な情報と考えるのが自然であり、`getTomorrowBaton` だけがバックフィルを除外する仕様は不具合の可能性が高い。

### 再現条件
1. 今日を `D`、昨日を `D-1` とする。
2. Archive の空き行または CalendarPicker から `D-1` を選び、`write/{date}` ルートでWrite画面を開く。Archive の空き行は `onBackfill(item.date.toString())` を呼び（`app/src/main/java/com/wineda/shiori/ui/archive/ArchiveScreen.kt:81-85`）、NavHostで `WriteForDate.createRoute(it)` に遷移する（`app/src/main/java/com/wineda/shiori/navigation/ShioriNavHost.kt:31-37`）。CalendarPicker も過去の空き日だけを選択可能にし（`app/src/main/java/com/wineda/shiori/ui/calendar/CalendarPickerViewModel.kt:47-62`）、確認ボタンで `onWritePast(date.toString())` を呼ぶ（`app/src/main/java/com/wineda/shiori/ui/calendar/CalendarPickerScreen.kt:98-100`, `app/src/main/java/com/wineda/shiori/ui/calendar/CalendarPickerScreen.kt:136-145`）。
3. `WriteViewModel` は `targetDate < today` のため `isBackfillMode = true`、新規Journalの `isBackfilled = true` として初期化する（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:45-57`）。
4. 「明日へのバトン」に文字列を入力して保存すると、`onFieldChanged()` と `markBackfillIfNeeded()` により `isBackfilled = true` が維持され、Repositoryも `journal.isBackfilled` をOR条件で保存値に反映する（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:82-102`, `app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:32-39`）。
5. Home を開くと `GetYesterdayBatonUseCase` は `D-1` の `tomorrow` を取得しようとするが、DAO条件 `isBackfilled = 0` に合わず `null` になり、`BatonCard` はフォールバックを表示する（`app/src/main/java/com/wineda/shiori/domain/usecase/GetYesterdayBatonUseCase.kt:10-12`, `app/src/main/java/com/wineda/shiori/data/local/dao/JournalDao.kt:27-28`, `app/src/main/java/com/wineda/shiori/ui/components/ShioriScaffoldBits.kt:134-136`）。

### 「今日を書く」から通常通り書いた場合の確認
通常の「今日を書く」は Home の `PrimaryShioriButton("今日を書く", onClick = onWrite)` から `write` ルートへ遷移する（`app/src/main/java/com/wineda/shiori/ui/home/HomeScreen.kt:112-114`, `app/src/main/java/com/wineda/shiori/navigation/ShioriNavHost.kt:19-29`）。`write` ルートには `date` 引数がないため、`WriteViewModel` は `targetDate = today`、`isBackfillMode = false`、`emptyJournal.isBackfilled = false` で初期化する（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:45-57`）。このまま同日内に新規保存される場合、Repository側の `createdDate` も同じローカル日付になり、`journal.date != createdDate` OR `journal.isBackfilled` は `false || false` なので `isBackfilled = false` で保存される（`app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:32-38`）。

ただし、一度 `isBackfilled = true` になった既存レコードを通常Write経路で開いても、`init` で `existing ?: emptyJournal` を採用するため既存の `isBackfilled = true` がJournalに残る（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:64-74`）。さらに `onFieldChanged()` とRepository保存ロジックがOR条件で維持するため、通常経路で編集しても `false` には戻らない（`app/src/main/java/com/wineda/shiori/ui/write/WriteViewModel.kt:82-102`, `app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:32-39`）。

## 5. 修正方針のヒント(参考のみ・実装はしないこと)
- 第一候補: `JournalDao.getTomorrowBaton()` から `AND isBackfilled = 0` を外し、`date` に対応する `tomorrow` を素直に返す。バックフィルであってもバトン本文は情報として有効なため。
- 空文字・空白だけの除外は UseCase の `takeIf { it.isNotBlank() }` に集約されているため、DAOは表示可否ではなくデータ取得に専念させるとよい。
- 併せて `isBackfilled` が一度 true になると戻らない仕様を明示するか、通常編集で戻す要件があるかを別途整理する。

## 6. ログ確認の提案(任意)
- `app/src/main/java/com/wineda/shiori/ui/home/HomeViewModel.kt:51-52` 付近に `today` と `getYesterdayBaton(today)` の戻り値を出すログを入れると、Home到達時の取得結果が分かる。
- `app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:32-39` 付近に `journal.date`, `createdDate`, `journal.isBackfilled`, 保存後の `isBackfilled` を出すログを入れると、保存時にバックフィル化しているか確認できる。
- DAOは `@Query` メソッド自体には直接ログを入れにくいため、`JournalRepository.getTomorrowBaton()`（`app/src/main/java/com/wineda/shiori/data/repository/JournalRepository.kt:30`）を一時的にブロック化して、対象日・戻り値をログ出力するとよい。

## 7. 追加で確認したい点
- 実際に表示されない端末のDBダンプで、昨日レコードの `date`, `tomorrow`, `isBackfilled`, `createdAt`, `updatedAt` を確認したい。
- 再現手順が「Archive/Calendarから昨日を後日入力」なのか、「前日の通常Writeで入力したのに翌日表示されない」なのかを切り分けたい。
- 端末のタイムゾーン変更、深夜をまたいだ入力、アプリ更新直後かどうか（破壊的マイグレーションの影響有無）を確認したい。
