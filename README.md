## リリースビルド手順

### 1. 署名キーストアを作成(初回のみ)

`keytool` でリリース用キーストアを生成する。**生成後は必ず別の場所にバックアップする。紛失するとアプリの今後のアップデートができなくなる。**

```bash
keytool -genkeypair -v \
  -keystore ~/.keystores/shiori-release.jks \
  -alias shiori \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -storetype JKS
```

### 2. キーストア情報を Gradle に渡す

リポジトリ直下に `keystore.properties` を作成(.gitignore で除外されている)。

```properties
SHIORI_KEYSTORE_FILE=/Users/<your-name>/.keystores/shiori-release.jks
SHIORI_KEYSTORE_PASSWORD=<store password>
SHIORI_KEY_ALIAS=shiori
SHIORI_KEY_PASSWORD=<key password>
```

または、環境変数として export しても同じ名前で参照される。`~/.gradle/gradle.properties` に同じキー名で設定しても参照される。

### 3. ビルド

```bash
./gradlew assembleRelease
```

成果物: `app/build/outputs/apk/release/app-release.apk`

### 4. 端末にインストール

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

または APK をスマホに転送して「不明なアプリのインストールを許可」してインストール。
