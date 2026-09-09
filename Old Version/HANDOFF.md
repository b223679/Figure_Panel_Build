# Figure Panel Builder 引継ぎ（2026-09-09）

## 現在の状態

今回の依頼は、受領したClaude Code版を取り込み、Insetの折りたたみ・ROI倍率モードと、独立したFree buildを実装するもの。実装と自動／実GUI検証を完了。使用方法はREADME、検証記録はVALIDATIONを参照。

作業ルート: `C:\Users\B223679\Documents\ChatGPT\microscopic figure`

受領した`引継ぎ資料/`は変更せずGitに保存した。そこに記載された`4b0738c`はこのリポジトリの履歴には存在しない。受領資料の「未コミット変更」等はClaude側の作業環境の記述であり、現在の状態は必ず`git status`で確認すること。

## Git・安全な作業

- `AGENTS.md`に従う。開始時にGit状態を確認し、既存の未コミット変更をreset/revert/deleteしない。論理単位でコミットする。
- `f63f429`が元の動作版baseline、`c741d3b`履歴機能、`a13dd1f`直接操作UI、`8c40414`検証・安全手順。受領資料baselineは`333bba5`。
- `.tools/`、`target/`、`dist/`、`artifacts/`はGit管理外。ソースと受領資料、ドキュメント、テストは管理対象。
- 通常は`build.ps1`で増分ビルド。以前、target配下のnode_modulesジャンクションをMaven cleanがたどって共有依存関係を削除した事故がある。共有依存は同一公式版で復旧・7,796ファイル検証済み。`artifacts/runtime-recovery/restoration.json`に記録あり。共有依存へのリンクをtarget配下に作らない。直接Maven cleanでbuild.ps1のリンク検査を迂回しない。
- 元TIFF・ユーザーの開いているFiji画像を変更／削除しない。Removeは構成から除外するだけ。
- FijiへのJARコピー後も、起動済みプロセスのクラスは更新されない。ユーザーの既存Fijiを勝手に終了・再起動しない。

## 実装の構造

### 通常モード

`FigurePanelBuilderDialog`がCondition×DisplayChannelのUIを管理。Source画像は`InputImageManager`が不変のピクセル複製を所有する。`FigureConfiguration.channels`のB&C／LUT／Invert設定を全Conditionが共有する。`FigureWorkspace`はドラッグ、選択、Swap、Trash、＋タイルを担当。

`FigureHistory`は設定スナップショットと入力インデックスを保存し、元の不変ピクセルを共有する。通常設定JSONは`SettingsSerializer`のversion 1を維持。

受領版のDesign名称、ドラッグ後のCondition選択枠修正、Inset行インデックスの並べ替え／削除追随も取り込んだ。Conditions×Conditionsの失敗した実装は取り込んでいない。

### Inset

- `InsetConfig`: 通常モードでは共通のROI・Inset外観。`sameAsRoi=true`と`magnification=3`が新規既定値。`boxWidth()/boxHeight()`で有効寸法を計算。自由サイズの値はモードを切り替えても保持する。
- `InsetCell`: 各Conditionの表示行ごとにenabled／ROI座標を保持。新規enabled=false。ConditionConfig.shareInsetRoi/sharedRoiX/sharedRoiYで同一Condition内の位置共有を設定する。FigureConfiguration.insetCellは表示オンオフを保持した有効座標を返す。
- `InsetPanel`: 最初にShow inset。オンで詳細展開。通常モードでは外観が共通、オンオフは選択パネル個別。ROI位置は個別またはCondition内で共有できる。Freeでは設定自体が個別。プレビューのROIはドラッグ／矢印で移動。
- `InsetRenderer`: ROIサイズ・倍率・位置から描画。同じROI形状モードで円形を選ぶと、Insetにも楕円クリップを適用。Customは矩形で独立寸法。出力と編集プレビューで共通処理。
- 枠・ROIが収まらない場合は受領版と同様、そのパネルのInsetを出さない。Insetタブでは収まらない旨を表示する。
- 旧JSONにinsetはあるがsameAsRoiがない場合、`SettingsSerializer.load`でCustom扱いにして旧寸法を維持する。

### Free build

- `FreeBuildConfiguration`: 独立したrows×columnsの`Panel`一覧。各Panelが「1 Condition・1 DisplayChannel」のFigureConfigurationを持つ。各画像設定のchannelsリストは独立し、通常モードの共有軸ロジックを使わない。
- `FreeBuildDialog`: 確認付きモード移行、新グリッド、空セル選択→ImageJ画像／TIFF→Channel選択、B&C、手入力ラベル、名前表示、Inset／スケール、出力。通常モードとは別のJFrameを使用し、移行前の編集画面は破棄する。切替をキャンセルしたら破棄しない。
- `applyTone`: 同一行／列の占有パネルに、対応する元Channel番号のmin/max/LUT/invertを値コピーする。空白・対応しないChannel・他の軸は変更しない。
- 画像は縦横比を保ってセル内に収める。セル全体の標準サイズは最初の画像から決め、右側で調整できる。全ラベルは手入力。名前表示モードだけは元画像のtitleを使い、出力にも反映する。
- `scene()`は画像／テキストの出力要素一覧。灰色セル・＋・選択枠はCanvasだけで描画し、sceneには入らない。未配置パネルのPanelラベルも出力しない。行列ラベルはFigure外周の独立要素。
- `FreeBuildSettings`: `mode=free-build, version=1`の別JSON。相対ソースパス、各パネルの画像設定、手入力名、空白セルを保存する。通常モードの設定とは相互変換しない。開いている画像を選んだ場合も不変snapshotを使用する。未保存画像からの設定JSON保存は既存と同様、元TIFFパスが必要。
- FreeのUndo/Redoは最大100操作。JSONスナップショット、不変入力の保持、600ms以内の同じ項目の連続編集の集約。配置・除外・ラベル・名前モード・B&C・Inset・スタイル・設定読込を戻せる。モード切替で履歴をリセットする。ファイル出力は対象外。
- `InputImageManager.include`はFree設定ロード後もUndo前の元画像を保持するための入力インデックス結合。

### 描画・出力

`ImageRenderer`はチャネル表示とROI切出し、`PanelLayoutEngine`は通常の図とFree各セルの生成に使用する。`PptxExporter`は通常の処理を維持し、テンプレート書込だけを共通化。Free用`saveFree`はsceneから個別画像と編集可能テキストを作成する。

通常PPTXでは従来どおりスケールが独立shape。PPTXのInsetは独立したcrop picture、ROI枠は独立したnative shape。TIFF／PNGの描画は従来どおり。Free PPTXのスケールも画像に含める。Freeの画像・手入力ラベル・画像名文字は独立オブジェクト。空白パネルのshapeはない。

設定・出力は元画像上書きを`OutputSafety`で禁止し、一時ファイルから置換する。Freeの画像読込・保存・プレビューはSwingWorker。既存画像の代わりにスナップショットを使う。

## ビルドと検証

```powershell
.\build.ps1
```

JDK既定値: `C:\Program Files\Fiji\java\win64\zulu21.42.19-ca-jdk21.0.7-win_x64`

Maven: `.tools/apache-maven-3.9.9/bin/mvn.cmd`、依存キャッシュ: `.tools/repository`。Java 8互換バイトコード。JARにはGsonをshadeし、ImageJ／SciJavaはFiji提供。

実GUI検証は既存Fijiへ接続せず、次を別プロセスで実行する。

```powershell
& 'C:/Program Files/Fiji/java/win64/zulu21.42.19-ca-jdk21.0.7-win_x64/bin/java.exe' -cp 'target/test-classes;target/figure-panel-builder-1.0.0.jar;C:/Program Files/Fiji/jars/*' org.microscopy.figure.FreeBuildUiValidation
& 'C:/Program Files/Fiji/java/win64/zulu21.42.19-ca-jdk21.0.7-win_x64/bin/java.exe' -cp 'target/test-classes;target/figure-panel-builder-1.0.0.jar;C:/Program Files/Fiji/jars/*' org.microscopy.figure.DirectManipulationValidation
```

最新検証はJUnit40件成功、両GUI検証成功。キー検証はSwingの登録Action経由で、OSキーボード注入ではない。Free PPTXは要素の自動検査と書込成功までで、PowerPoint本体による今回の確認は未実施。

成果物と検証出力:

- `target/figure-panel-builder-1.0.0.jar`: ビルド結果
- `dist/figure-panel-builder-1.0.0.jar`: 配布用
- `C:\Program Files\Fiji\plugins\figure-panel-builder-1.0.0.jar`: インストール先
- `artifacts/free-ui-3704902470794735903/`: Freeの最終画面・TIFF・PNG・PPTX・設定JSON
- `artifacts/direct-ui-11750141394290705571/`: 通常モード回帰検証

## 制約・次の作業で注意する点

- Free入力も2D TIFF（8/16/32-bit）、Z/TやRGB入力は未対応。通常と異なり画像サイズ・Channel数が異なっていてよい。
- Freeは1〜20行／列、1セル16〜8192px、出力合計1億px上限。上限を超えた場合はセル寸法・行列数を減らす。
- 空白出力は選択された白／黒のFigure背景。Freeは透過背景には対応していない。通常モードのPNG透過機能は維持。
- 未校正の画像のスケールバーにはFallback µm/pxが必要。
- 通常モードの列／行Drag & Dropは維持。Freeは固定グリッドへの個別割当であり、通常の行列Swap／Drag & Dropは搭載しない。
- 追加の仕様変更は現在のコードを基に段階的に行う。受領資料のソースを再び一括上書きすると今回の変更が失われるため、差分を確認すること。

## UI・Inset追補

ROIとInsetの枠初期太さは長辺の0.5％。新規位置はTOP_RIGHT。既存JSONの指定値は維持する。InsetPanelは左右のセクションを等幅にし、項目を左揃え。右ペイン490pxでSame as ROI／Custom sizeの両方を検証。通常／FreeのモードボタンはBorderLayout.EASTで右端に配置。OpenImageSelectionを両モードから呼び、通常は複数・Freeは1画像を選択する。

PptxExporter.insetが別画像・ROI shapeを両モードへ出力する。Freeのscene(inputs,scale,true)はInsetなしの元画像とInset描画用メタデータを返す。通常のsceneは変更せずTIFF／PNGの見た目を維持する。ROI共有解除では全表示行へ現在の座標を値コピーし、その後は個別に編集できる。
