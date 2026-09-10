# Visual Fig Builder — 顕微鏡画像のFigure Panelを直感的操作で作成

**論文・発表用に顕微鏡画像をFijiで直感的かつすばやく並べ、PowerPointで各要素を編集できます。**

Visual Fig Builderは、蛍光・免疫染色画像からFigure Panelを作成するFijiプラグインです。使いたい画像を追加し、ドラッグ＆ドロップで並べ替え、単ChannelやMerge表示を選んで、完成形を確認しながら図を組み立てられます。

行・列の並べ替えや軸の入れ替え、不要な行・列の除去、注目領域のインセット作成まで、同じ画面で操作できます。ラベル、余白、背景、LUT、明るさ・コントラスト、スケールバーもプレビューを見ながら調整できます。

## Visual Fig Builderの特長

- **見ながら組み立てる直感的な操作**：ドラッグ＆ドロップによる配置変更、行・列の入れ替え、最大100操作のUndo・Redoに対応します。
- **柔軟なChannel・Merge選択**：単独のChannelや複数ChannelのMergeを表示項目として追加できます。各Channelの明るさ・コントラストとLUTを全条件で共通に保ちます。
- **インセット機能**：長方形・円形のROIで注目領域を指定し、拡大像をインセットで配置できます。
- **カスタムデザイン**：画像間の余白、行・列ラベル、スケールバーとバーテキストを自由に設定できます。背景色は白・黒・透過背景から選択できます。
- **TIFF・PNG・編集可能なPowerPointへ出力**：作成したFigure Panelは、RGB TIFF・PNG・PPTXで保存できます。PPTXでは各画像、ラベル、スケールバー、スケールバーの文字、インセットを独立したPowerPointオブジェクトとして配置するため、PowerPointでさらに自由に編集することができます。

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

1. [最新版のプラグインJAR](https://github.com/b223679/Figure_Panel_Build/raw/refs/heads/master/dist/figure-panel-builder-1.0.0.jar)をダウンロードして、Fiji の `plugins` フォルダにコピーします。既存の同名JARがあれば置き換えます。
2. Fiji を再起動します。
3. メニューから `Plugins > Visual Fig Builder` を選択して起動します。

---

JARのファイル名と内部クラス名は互換性のため従来の名前を維持しています。メニューとウィンドウの表示名は **Visual Fig Builder** です。設定JSONの形式と画像の出力処理は従来どおりです。

## クイックスタート

### サンプルファイルで試す

1. [Releases ページ](https://github.com/b223679/Figure_Panel_Build/releases/latest)の **Assets** から `figure_panel_builder_testset.zip` をダウンロードして、任意のフォルダに**すべて展開**します。
2. プラグインを起動し、最初の画像選択ダイアログを閉じて `Load settings` から展開先の `example-settings.json` を開きます。
3. **Image A / Image B / Image C × Green / Red / Merge** のサンプルが表示されます。

ZIP には設定 JSON と合成画像 `Image A.tif`・`Image B.tif`・`Image C.tif` が入っています。展開後も4ファイルを同じフォルダに置いてください。

JSON に画像データは含まれないため、4 ファイルすべてが必要です。Releases の **Source code (zip)** をダウンロードして展開する場合は、同梱の `test-data/example-settings.json` をそのまま開けます。

### 基本操作

1. 起動直後に `Select Images` ダイアログが開きます。
2. Fiji で既に開いている画像を Ctrl / Shift クリックで選択、または `Open TIFF files...` でファイルを選択します（ドラッグ＆ドロップも可）。
3. `＋ Channel / Merge` で表示チャネルを追加します。
4. プレビューを確認しながら B&C・LUT・ラベル・スケールバーを調整します。
5. `Save PNG...` / `Save PPTX...` / `Save RGB TIFF...` で出力します。

---

## 操作方法

### 画像の追加と管理

- **Select Images**：Fiji で開いている画像か TIFF ファイルを選択して追加します。プラグイン起動時に自動表示され、上部メニューバーのボタンから開くことができます。プレビュー右の `＋ Condition` ボタンでも同じ画面が開きます。
- **画像の並べ替え**：画像を横方向にドラッグすると列（Condition）を、縦方向にドラッグすると行（Channel）を移動します。最初の移動方向で対象が確定します。
- **行/列の非表示**： `B&C` パネルの右にゴミ箱アイコンが表示されます。ドラッグした行または列をゴミ箱にドロップすることでFigureパネルから削除できます。Fijiで開いているファイルは開いたままになります。

### チャネルの設定

| 操作 | 説明 |
|---|---|
| `＋ Channel / Merge` | 元チャネルをチェックして追加。複数チェックで Merge になります |
| ラベルのダブルクリック | チャネル名と LUT を編集（Merge は構成チャネルごとに設定）|
| `Swap` | 行軸と列軸（Channel ↔ Condition）を入れ替えます |

### 明るさ・コントラスト

- プレビューの画像セルをクリックするとそのチャネルの B&C パネルが下部に表示されます。
- 上部メニューバーの `B&C` をクリックすることでB&Cパネルを開閉できます。
- **B&C は全条件共通**です。特定の条件だけ値を変えることはできません。
- **Auto**：全条件の有限画素の最小値・最大値を使って自動設定します。
- **Reset**：8-bit → 0–255、16-bit → 0–65535、32-bit → 全条件のデータ範囲に戻します。
- 元画像の画素値は変更しません。
- Merge チャネルでは `Channel` 欄から調整する元チャネルを選択します。
- Min / Max / LUT 変更はリアルタイムにプレビューへ反映されます。

### ラベル編集

- **ラベルのクリック**：その行・列ラベル領域に青い選択枠が表示されます（出力には含まれません）。
- **ラベルのダブルクリック**：条件名を編集できます。
- 上部メニューバーの `Design` ボタンをクリックすることで右ペイン（ラベル・スケールバー・Inset 設定）を開閉できます。
- 右ペインの**Label name タブ**から現在のラベルの確認・編集や、ソース画像の確認ができます。
- 右ペインの**Style タブ**からラベルのスタイルを設定できます。
- 長いラベルはフォントサイズを上限として自動縮小します。設定済みフォントサイズ自体は変わりません。


### スタイル設定（`Style` タブ）

右ペインの`Style` タブでは以下をまとめて設定できます。

| セクション | 主な設定項目 |
|---|---|
| Background | White / Black / Transparent |
| Gap | 画像間余白 |
| Labels | 行・列ラベルの表示 ON/OFF・フォントサイズ・位置・色 |
| Scale bar | 長さ・太さ・位置・余白・適用範囲・テキスト表示 |

行ラベルは左配置でBottom → Top、右配置でTop → Bottom に表示されます。

---

## 元に戻す・やり直し

| 操作 | ショートカット |
|---|---|
| Undo | Ctrl + Z または上部の ← アイコン |
| Redo | Ctrl + Y または上部の → アイコン |

対象操作：Condition / Channel の追加・削除・並べ替え・Swap・名前 / LUT / B&C / Invert gray / Style 変更・ソース変更・設定読込。直近 100 操作を保持し、連続する同チャネルの B&C 調整はまとめます。プラグインを閉じると履歴は破棄されます。出力ファイルの保存は Undo の対象外です。

---

## 出力形式

### RGB TIFF出力（`Save RGB TIFF`）

RGB TIFF を生成します。透過には対応していません。

### PNG出力（`Save PNG`）

- White / Black / Transparent 背景を選択できます。
- 透過時はアルファチャンネル付き PNG として保存されます。
- プレビューでは透過部分を市松模様で表示します。

### PowerPoint出力（`Save PPTX`）

- スライドのアスペクト比はFigureと同じになります。
- 各セル画像・ラベル・スケールバー・バーテキスト・インセットが独立したオブジェクトとして配置されます。
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
| Every cell | 全画像に表示 |
| One per condition | 各条件の最後の表示チャネルに表示 |
| Selected cell | クリックして選択した画像だけに表示 |
| Figure once | 右下の画像のみに表示 |

---

## 設定の保存と読み込み

| ボタン | 動作 |
|---|---|
| `Save settings` | 現在の設定を JSON ファイルに保存 |
| `Load settings` | 保存済み JSON から設定を復元 |

**JSON は対応する画像と同じフォルダに保存してください。**
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

`dist/figure-panel-builder-1.0.0.jar`・`dist/figure_panel_builder_testset.zip` と `test-data/` のサンプルは Git 管理対象です。サンプルを更新した際は `./package-testset.ps1` で配布 ZIP を更新してください（画像は再生成せず、現在の4ファイルをまとめます）。`target/` はビルド出力、`artifacts/` は再生成可能な検証画像・出力の保存先で、どちらも配布には不要です。`generate-test-data.ps1` はサンプルの再生成用なので、通常のインストールでは実行不要です。

公開時は GitHub Releases でバージョンのタグを指定し、上記 JAR とサンプル ZIP を Assets に添付してください。README のプラグイン配布リンクは、このリポジトリの検証済みJARを直接取得します。サンプルのリンクは最新のリリースページを開きます。

詳細は [`IMPLEMENTATION_PLAN.md`](docs/development/IMPLEMENTATION_PLAN.md)・[`VALIDATION.md`](docs/development/VALIDATION.md) を参照してください。


## ファイル選択の初期フォルダ
画像・設定の保存と設定の読み込みは、選択中の画像のフォルダを初期表示します。未選択の場合は図で使用中の画像、次に Fiji の現在の画像を参照し、有効な保存元がなければ標準フォルダを使用します。通常モード・Free build 共通です。

## 自由配置モード
上部メニューバーの右から `Free mode` が選択できます。Free modeは現在未完成です。
