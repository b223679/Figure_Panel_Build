# Fiji Figure Panel Builder

顕微鏡画像（TIF/TIFF）を **Condition × Display Channel** のグリッドに自動配置し、ラベル・スケールバーを付けた論文・学会発表用フィギュアを出力する Fiji/ImageJ プラグインです。出力形式は RGB TIFF・PNG・編集可能な PPTX の 3 種類。元画像の画素は一切変更しません。

---

## 動作環境

| 項目 | 要件 |
|---|---|
| OS | Windows（推奨）|
| Fiji | ImageJ 1.54p 以降 |
| Java | JDK 8 互換（Fiji 付属 JDK 21 で検証済み）|
| SciJava | 2.100.1 以降 |

---

## インストール

1. [figure-panel-builder-1.0.0.jar をダウンロード](https://github.com/b223679/Figure_Panel_Build/raw/refs/heads/master/dist/figure-panel-builder-1.0.0.jar)して、Fiji の `plugins` フォルダにコピーします。
2. Fiji を再起動します。
3. メニューから `Plugins > Figure Panel Builder` を選択して起動します。

---

## クイックスタート

### 設定ファイルから試す

1. [example-settings.json をダウンロード](https://github.com/b223679/Figure_Panel_Build/raw/refs/heads/master/test-data/example-settings.json)します。ブラウザに内容が表示される場合は、リンクを右クリックして「名前を付けてリンク先を保存」してください（拡張子は `.json`）。
2. 合成サンプル画像 [Control.tif](https://github.com/b223679/Figure_Panel_Build/raw/refs/heads/master/test-data/Control.tif)・[HPR.tif](https://github.com/b223679/Figure_Panel_Build/raw/refs/heads/master/test-data/HPR.tif)・[KO.tif](https://github.com/b223679/Figure_Panel_Build/raw/refs/heads/master/test-data/KO.tif) もダウンロードし、**JSON と同じフォルダ**に元のファイル名で保存します。
3. プラグインを起動し、最初の画像選択ダイアログを閉じて `Load settings` から保存した `example-settings.json` を開きます。**3 条件 × Green / Red / Merge** のサンプルが表示されます。

JSON に画像データは含まれないため、4 ファイルすべてが必要です。[リポジトリ全体を ZIP でダウンロード](https://github.com/b223679/Figure_Panel_Build/archive/refs/heads/master.zip)して展開する場合は、同梱の `test-data/example-settings.json` をそのまま開けます。

### ゼロから作る

1. 起動直後に `Select Images` ダイアログが開きます。
2. Fiji で既に開いている画像を Ctrl / Shift クリックで選択、または `Open TIFF files...` でファイルを選択します（ドラッグ＆ドロップも可）。
3. `＋ Channel / Merge` で表示チャネルを追加します。
4. プレビューを確認しながら B&C・LUT・ラベル・スケールバーを調整します。
5. `Save PNG...` / `Save PPTX...` / `Save RGB TIFF...` で出力します。

---

## 基本操作

### 画像の追加と管理

- **Select Images**（起動時自動表示・上部ボタンからも開ける）：Fiji で開いている画像か TIFF ファイルを選択して追加します。
- **＋ Condition**：条件（サンプル）を追加します。Figure タブ上部のボタンからも同じ画面を開けます。
- **ドラッグ並べ替え**：画像を横方向にドラッグすると列（Condition）を、縦方向にドラッグすると行（Channel）を移動します。最初の移動方向で対象が確定します。
- **ゴミ箱へドロップ**：確認後、Condition または Channel / Merge をフィギュアから除外します。元ファイルは削除しません。

### チャネルの設定

| 操作 | 説明 |
|---|---|
| `＋ Channel / Merge` | 元チャネルをチェックして追加。複数チェックで Merge になります |
| チャネルラベルのダブルクリック | チャネル名と LUT を編集（Merge は構成チャネルごとに設定）|
| `Swap` 矢印 | 行軸と列軸（Channel ↔ Condition）を入れ替えます |

### Brightness & Contrast

- プレビューの画像セルをクリックするとそのチャネルの B&C パネルが下部に表示されます。
- **B&C は全条件共通**です。特定の条件だけ値を変えることはできません（科学的整合性のため）。
- **Auto**：全条件の有限画素の最小値・最大値を使って自動設定します。
- **Reset**：8-bit → 0–255、16-bit → 0–65535、32-bit → 全条件のデータ範囲に戻します。
- 元画像の画素値は変更しません。
- Merge チャネルでは `Channel` 欄から調整する元チャネルを選択します。
- Min / Max / LUT 変更はリアルタイムにプレビューへ反映されます。

### ラベル編集

- **ラベルのクリック**：その行・列ラベル領域に青い選択枠が表示されます（出力には含まれません）。
- **条件ラベルのダブルクリック**：条件名を編集できます。
- 長いラベルはフォントサイズを上限として自動縮小します。設定済みフォントサイズ自体は変わりません。
- **Design ボタン**：右ペイン（ラベル・スケールバー・Inset 設定）を開閉します。

### Style タブ

`Style` タブでは以下をまとめて設定できます。

| セクション | 主な設定項目 |
|---|---|
| Background | White / Black / Transparent |
| Gap | セル間余白 |
| Labels | 行・列ラベルの表示 ON/OFF・フォントサイズ・位置・色 |
| Scale bar | 長さ・太さ・位置・余白・適用範囲・テキスト表示 |

行ラベルは左配置で反時計回り（Bottom → Top）、右配置で時計回り（Top → Bottom）に表示されます。

---

## Undo / Redo

| 操作 | ショートカット |
|---|---|
| Undo | Ctrl + Z または上部の ← アイコン |
| Redo | Ctrl + Y または上部の → アイコン |

対象操作：Condition / Channel の追加・削除・並べ替え・Swap・名前 / LUT / B&C / Invert gray / Style 変更・ソース変更・設定読込。直近 100 操作を保持し、連続する同チャネルの B&C 調整はまとめます。プラグインを閉じると履歴は破棄されます。出力ファイルの保存は Undo の対象外です。

---

## 出力形式

### Save RGB TIFF

従来の Generate Figure と同等の処理で RGB TIFF を生成します。透過には対応していません。

### Save PNG

- White / Black / Transparent 背景を選択できます。
- 透過時はアルファチャンネル付き PNG として保存されます。
- プレビューでは透過部分を市松模様で表示します。

### Save PPTX

- スライドのアスペクト比はフィギュアと同じになります。
- 各セル画像・ラベル・スケールバー・バーテキストが独立したオブジェクトとして配置されます。
- PowerPoint で個別に移動・文字編集が可能です。
- Merge チャネルのラベルは各チャネル色で色分けされたテキストボックスになります。
- Transparent 背景時はスライドの塗りを設定しません（PowerPoint のスライド用紙は通常白く表示されます）。

> **注意**：元の TIFF ファイルへの上書き保存は禁止されています。

---

## スケールバーの設定

- 画像に pixel calibration が含まれる場合は自動取得します（nm / µm / mm → µm に変換）。
- 未校正の画像では `Manual µm/px` を手入力してください。
- 条件ごとに校正値が異なる場合は、プレビューのステータスと生成時に警告が表示されます。
- スケールバー長の初期値は **20 µm**、線幅は画像長辺の約 3%、文字サイズは約 10% です。

**適用範囲の選択肢：**

| 設定値 | 説明 |
|---|---|
| Every cell | 全セルに表示 |
| One per condition | 各条件の最後の表示チャネルに表示 |
| Selected cell | クリックして選択したセルだけに表示 |
| Figure once | 右下セルのみに表示 |

---

## 設定の保存と読み込み

| ボタン | 動作 |
|---|---|
| `Save settings` | 現在の設定を JSON ファイルに保存 |
| `Load settings` | 保存済み JSON から設定を復元 |

JSON にはファイルパスと設定値を保存します。画素データは含みません。Fiji で開いた画像（ファイルなし）は JSON に保存できません。

---

## 対応画像形式と制限事項

- **対応**：8 / 16 / 32-bit グレースケール TIF（XY または C×XY）
- **非対応・拒否**：Z > 1 または T > 1 の画像、RGB 入力
- **必須**：全 Condition で画像サイズ・チャネル数が一致すること

最終キャンバスは **100 メガピクセル** を上限としています。プレビューは縮小表示のため、微細構造の最終確認はフル解像度の生成画像で行ってください。

---

## 非破壊設計

- 元画像の画素値は一切変更しません。
- B&C と LUT は表示変換としてのみ適用し、新規 RGB キャンバスにのみ描画します。
- ROI・Overlay の転写は行いません。
- 設定 JSON には画素データを含みません。

---

## 構成クラス

| クラス | 責務 |
|---|---|
| `FigurePanelBuilderCommand` | SciJava メニュー登録・Swing 起動 |
| `FigurePanelBuilderDialog` / `AppearancePanel` / `ContrastPanel` | GUI・共通 B&C・横並びプレビュー |
| `InputImageManager` / `Source` | 入力と独立した画素コピー・メタデータ |
| `FigureConfiguration` / `ConditionConfig` | 自動グリッド・軸・Condition→Source 対応 |
| `ChannelConfig` / `DisplayChannel` | 全条件共通 B&C / LUT・Single / Merge の参照 |
| `ImageRenderer` | チャネル抽出・表示変換・加算 RGB Merge |
| `PanelLayoutEngine` / `LabelRenderer` / `ScaleBarRenderer` | キャンバス・回転ラベル・校正バー |
| `PreviewRenderer` | 縮小描画・全条件ヒストグラム |
| `SettingsSerializer` / `OutputSafety` | JSON 往復・元 TIF 上書き防止 |

---

## ビルド

JDK 8 互換バイトコードを JDK 21 と Maven 3.9.9 で生成します。ImageJ / SciJava は Fiji が提供し、Gson は名前空間を変更して JAR 内に同梱します。

```powershell
.\build.ps1
.\generate-test-data.ps1
```

`build.ps1` は既存の `JAVA_HOME` を優先し、未設定の場合は Fiji 付属 JDK を使用します。`.tools` に Maven がない場合は PATH の `mvn.cmd` を使用します。標準 Maven 環境では `mvn verify` でビルドできます。

ビルド結果は `target/figure-panel-builder-1.0.0.jar` です。配布版を更新するときは、`verify` 成功後に次のコマンドでコピーし、ソースと合わせてコミットしてください。

```powershell
Copy-Item -LiteralPath target/figure-panel-builder-1.0.0.jar -Destination dist/figure-panel-builder-1.0.0.jar
```

`dist/figure-panel-builder-1.0.0.jar` と `test-data/` のサンプルは Git 管理対象です。`target/` はビルド出力、`artifacts/` は再生成可能な検証画像・出力の保存先で、どちらも配布には不要です。`generate-test-data.ps1` はサンプルの再生成用なので、通常のインストールでは実行不要です。

詳細は [`IMPLEMENTATION_PLAN.md`](docs/development/IMPLEMENTATION_PLAN.md)・[`VALIDATION.md`](docs/development/VALIDATION.md) を参照してください。


## ファイル選択の初期フォルダ
画像・設定の保存と設定の読み込みは、選択中の画像のフォルダを初期表示します。未選択の場合は図で使用中の画像、次に Fiji の現在の画像を参照し、有効な保存元がなければ標準フォルダを使用します。通常モード・Free build 共通です。
