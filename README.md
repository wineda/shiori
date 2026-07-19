# 栞（しおり）

> 明日のわたしへの伝言。その場の**呟き**と一日の**振り返り**を、言葉だけで束ねる日記。

和紙色と墨色の無彩色に、栞紐の臙脂＝**栞紅 #9C4A50** をひとしずく。気分の入力は置かず、書くことそのものに集中する、静かなジャーナリング PWA です。

## 特徴

- **4タブ**：呟き（即時の一言・ゴシック）／振り返り（一日の内省・明朝）／履歴（件数ヒートマップのカレンダー）／うつろい（期間のAI読み解き）。
- **AIはキー不要の「共有→貼り付け」方式**：サーバーもAPIキーも持たず、OSの共有シートで手持ちのAIアプリ（Claude / ChatGPT / Gemini など）に渡し、返信を貼り付けて取り込む。AI出力は必ず下書きで、自動保存しない。
- **オフラインで動く PWA**：ホーム画面に追加してアプリのように起動。フォントも同梱し、通信なしで描画。
- **消えにくい保存**：記録は IndexedDB に保存。ブラウザの「キャッシュ削除」では消えません（アプリ本体のキャッシュとは別枠）。
- **持ち出せる**：Markdown 書き出し（読む用）と JSON バックアップ／復元（丸ごと戻す用）。

## 技術構成

素の **HTML / CSS / JavaScript**。ビルドツール・フレームワーク・実行時の外部依存は **なし**。

| ファイル | 役割 |
|---|---|
| `index.html` | マークアップ |
| `styles.css` | スタイル（`@font-face` 含む） |
| `app.js` | ロジック（保存・描画・AIブリッジ・PWA） |
| `manifest.webmanifest` | PWA マニフェスト |
| `service-worker.js` | アプリ本体をキャッシュしてオフライン起動 |
| `fonts/` | 自己ホストの日本語フォント（woff2）＋ ライセンス |
| `icons/` | アプリアイコン（ロゴC・192/512・maskable） |

保存キー：`journal:day:YYYY-MM-DD` ／ `journal:settings` ／ `journal:insight:*`

## ローカルで動かす

Service Worker は `file://` では動かないため、簡易サーバー経由で開きます。

```bash
python3 -m http.server 8000
# → http://localhost:8000/index.html
```

（`npx serve` など任意の静的サーバーでも可）

- 初回起動時に過去約20日分のサンプルデータを種まきします（設定から「まっさらに」で初期化可）。
- 動作確認：DevTools → Application で **Manifest** / **Service Workers**（activated）/ **Cache Storage**（`shiori-shell-v1`）/ **IndexedDB**（`shiori`）を確認。Network を Offline にしてリロードしても起動します。

## ビルド

**ビルド工程はありません。** 静的ファイルをそのまま配信します。

フォントだけは配布容量のため、実使用の5フェイス（明朝 400/500・ゴシック 400/500/700）を woff2 にサブセット化して同梱しています。作り直す場合の一度きりの手順（開発時のみ・アプリには不要）：

```bash
pip install fonttools brotli
# 例：Shippori Mincho Regular を全字サブセット化
pyftsubset ShipporiMincho-Regular.ttf \
  --unicodes="U+0020-007E,U+00A0-00FF,U+2000-206F,U+3000-30FF,U+3190-31FF,U+3200-33FF,U+4E00-9FFF,U+F900-FAFF,U+FE30-FE4F,U+FF00-FFEF" \
  --layout-features='kern,liga,palt,vert,vrt2,locl' --flavor=woff2 \
  --output-file=fonts/ShipporiMincho-400.woff2
```

## デプロイ

**任意の静的ホスティング**に置くだけで公開できます（GitHub Pages / Netlify / Cloudflare Pages / 任意の S3+CDN など）。

- Service Worker と PWA インストールには **HTTPS**（または localhost）が必要です。ほとんどの静的ホストは HTTPS 標準対応。
- 資産は相対パスで記述しているため、ルート直下でもサブディレクトリ配信でも動きます。

## PWA としてインストール

- **Android（Chrome/Edge）**：初回に出る「追加」バナー、またはアドレスバーのインストールアイコンから。
- **iOS（Safari 16.4+）**：共有 ⬆︎ →「ホーム画面に追加」。通知はインストール後かつ iOS 16.4 以降が前提。
- 未インストールの iOS Safari では、長期間未使用（約7日）でサイトデータが消える可能性があります（ITP）。ホーム画面に追加すると回避でき、加えて JSON バックアップで機種変・紛失にも備えられます。

## データとプライバシー

- 記録は端末内の IndexedDB にのみ保存。サーバーへ送信しません（AIは共有シート経由で、送る内容はユーザーが確認）。
- 初回に `navigator.storage.persist()` で自動削除の対象外を要求します（許可されるかはブラウザ判断）。
- バックアップ：設定 →「バックアップを保存（JSON）」で画像も含め丸ごと書き出し。復元は「マージ／上書き」を選択。

## ライセンス

- アプリのコード：`LICENSE` を参照。
- 同梱フォント：**SIL Open Font License 1.1**
  - Shippori Mincho — `fonts/OFL-ShipporiMincho.txt`
  - Zen Kaku Gothic New — `fonts/OFL-ZenKakuGothicNew.txt`
