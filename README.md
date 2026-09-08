# Fiji Figure Panel Builder

Condition × DisplayChannelから、元画像を変更せずRGB TIFF・PNG・編集可能なPPTXのfigureを作成するSciJava/Swingプラグインです。

## 2026-09-09: Inset / Free build

- 提供されたClaude Code版のInset・Designタブ・ドラッグ選択枠修正を取り込みました。通常モードの既存機能は維持しています。
- Design内のInsetタブは、パネル選択後に「Show inset」をオンにすると設定が展開します。初期値はオフです。
- Insetサイズは「Same as ROI」が初期設定。ROIの縦横比と形状を維持し、Magnificationで拡大倍率を指定します（初期3倍、1〜100倍）。円形ROIなら楕円／円形Insetになります。「Custom width / height」では矩形の縦横を独立に指定できます。旧Inset設定JSONは従来の幅・高さを維持します。
- 上部の「Free mode OFF」を押すと、Figure消去の確認後に行数・列数を入力します。通常のCondition×Channelとは独立した編集画面になります。OFFへ戻る場合も確認し、空の通常Figureから開始します。確認・グリッド入力をキャンセルすれば現在のFigureを維持します。
- Free buildの空きパネルをクリックし、TIFF→Channelの順に選択します。複数Channel選択でMerge。配置済み画像はクリックで設定を編集し、ダブルクリック／Select Imageで差し替えます。画像サイズやChannel数が異なるTIFFも使え、縦横比を保ってパネル内に収めます。
- B&C / LUT / Invert grayはパネルごとに独立。「Apply B&C / LUT to row / column」で、そのパネルにあるChannel番号に対応する設定を同じ行／列へコピーします。空きパネル・対応しないChannelは変更しません。
- 行・列・パネルラベルは手入力です。「Show image name instead of image」は選択パネルだけをファイル名表示へ切り替え、保存結果にも反映します。
- 灰色背景・＋・選択枠は編集画面専用。空きパネルは出力時にFigure背景だけとなり、PPTXには空きパネルのオブジェクトを作りません。
- Free buildにも個別Inset／スケールバー、TIFF・PNG・PPTX出力、設定JSON、Undo/Redoを用意しています。Free build設定は通常設定と別形式なので対応するモードで読み込んでください。
- PPTXの各画像・手入力ラベル・ファイル名は独立して移動／編集可能です。Insetは画像に焼き込みます。Free buildのスケールバーもパネル画像に含みます（通常モードのPPTXスケールバーは従来どおり独立オブジェクト）。

実装・検証・引継ぎの詳細は [HANDOFF.md](HANDOFF.md) と [VALIDATION.md](VALIDATION.md) を参照してください。

## Fijiへの導入

1. `dist/figure-panel-builder-1.0.0.jar`をFijiの`plugins`フォルダにコピーします。
2. Fijiを再起動します。
3. `Plugins > Figure Panel Builder`を開きます。

Windows上のFiji付属JDK 21、ImageJ 1.54p、SciJava 2.100.1で検証しています。2026-09-08版は、プレビュー中心のUI、＋タイル、ラベル編集、ドラッグ並べ替え、プレビュー下の共通B&Cを追加しています。

## プレビューから操作する

- 新規作成では最初の画像の長辺に応じて初期サイズを設定します。行・列ラベルとスケールバー文字は約10％（1024pxなら102px）、バー線幅は約3％（1024pxなら31px）、余白は約2％です。バー長の初期値は20µmです。未校正の場合はManual µm/pxの設定が必要です。読み込んだ設定や、初期値から手動変更した値は維持します。
- ラベルをクリックすると、そのラベル領域に青い選択枠が表示されます。条件名・チャネル名のどちらでも利用でき、枠は出力画像には含まれません。
- StyleタブはBackground、Gap、Labels、Scale barの順です。白／黒／透過や上下左右を選択し、行・列ラベルとバーの表示をOFFにすると詳細設定が折りたたまれます。スケールバーのLocationには位置・余白・適用範囲をまとめています。

- 起動直後にSelect Imagesを開きます。Fijiで開いている画像をCtrl / Shiftで複数選択して追加できます。上部のSelect Images・Figureの「＋ Condition」も同じ選択画面を開きます。選択画面のOpen TIFF files...またはファイルのドラッグ＆ドロップで、従来のTIFF読込も利用できます。
- 新規作成は最初のチャネル1行から始まります。「＋ Channel / Merge」で表示する元チャネルを選び、複数チェックするとmergeになります。
- 2つの＋タイルの間の矢印「Swap」で軸を交換します。＋タイルの追加対象も入れ替わります。
- 画像をクリックすると、そのチャネルのB&Cを下に表示します。mergeではChannel欄から調整する元チャネルを選びます。Min / Max / Brightness / Contrast、LUT変更は全条件共通でライブ反映されます。
- Autoは全条件の有限画素の最小値・最大値を使用します。Resetは8-bitで0–255、16-bitで0–65535、32-bitで全条件のデータ範囲に戻します。元画像の画素は変更しません。
- チャネルラベルをダブルクリックすると名前とLUTを編集できます。mergeでは構成チャネルごとの名前・LUTをまとめて編集します。表示は各LUT色の名前を「/」で結合します。条件ラベルもダブルクリックで編集できます。
- 長いラベルは、設定したフォントサイズを上限に文字だけ縮めてセルに収めます。画像サイズや保存したフォント設定は変えません。
- 画像を横方向にドラッグすると列を、縦方向にドラッグすると行を移動します。最初の移動方向で対象を固定し、ラベルからのドラッグではそのラベルの行／列を対象にします。対象は半透明と黄色い枠、挿入先は水色の線で表示します。Figure外で離すかEscでキャンセルできます。
- B&C右側のゴミ箱へドロップすると、確認後にConditionまたはChannel / MergeをFigureから除外します。元ファイル・元画像は削除しません。軸交換後も同じ操作を使用できます。
- 「B&C」で調整パネルを、「Labels / Scale」で右ペインを開閉できます。右側のLabel nameタブはCondition表とChannel表を同時に表示し、外側の縦スクロールを使いません。件数が多いときは各表の中だけをスクロールします。
- Channel表はFigureの表示順にName / LUT / Channel No.を表示します。単独チャネルは名前・LUTを表で編集でき、mergeは名前またはLUTをダブルクリックして各元チャネルを編集します。B&C・LUT・Invert grayは元チャネル単位で全Conditionに共有します。表示していない元チャネルもB&CのChannel選択やmerge追加で使用できます。
- ＋タイル、Swap、ゴミ箱、ドラッグ表示、操作説明、選択枠はUIだけに表示し、生成・保存した図には含めません。

## Undo / Redo

Ctrl+Z / Ctrl+Y、または上部の矢印アイコンでUndo / Redoを実行します。Condition・Channel / Mergeの追加／削除、並べ替え、Swap、名前・LUT・B&C・Invert gray・Style・ソース変更・設定読込を対象とします。直近100操作を保持し、短時間に連続する同じチャネルのB&C調整はまとめます。Undo後に新しい編集をするとRedo履歴は置き換わります。

履歴には設定と不変の画像スナップショットへの参照を保持し、操作ごとに画像を複製しません。履歴はプラグインを閉じると破棄します。出力ファイルの生成・保存そのものはUndo対象ではありません。

## PNG・PPTX出力

- 起動時にタスクバーを除く画面領域で最大化します。B&Cにはヒストグラムを表示しません。
- BackgroundからWhite / Black / Transparentを選びます。透過部分はプレビューだけ市松模様で表示し、`Save PNG...`ではアルファ透明度を保存します。画像セルそのものは不透明です。
- `Save PPTX...`は図と同じ縦横比の1枚のスライドに、各セルの元解像度PNG・行列ラベル・スケールバー・バー文字を別オブジェクトで配置します。PowerPointで個別に移動・文字編集できます。Mergeラベルは1つのテキストボックス内で各チャネル名を色分けします。
- PPTXのTransparentは背景の塗りを設定しません。PowerPointのスライド用紙は通常白く表示されますが、画像・ラベルを別のスライドへ移動すると余白の背景は付きません。透明なラスター画像が必要ならPNGを選びます。
- Generate TIF / RGB TIFFは透明度を保存できないため、透過設定時はPNGまたはPPTXへ案内します。
- 白背景のYellow/Cyan/Greenラベルは暗め、黒背景のBlueラベルは明るめに補正します。画像のLUTは変更しません。透過時のラベル色は元のLUT色です。条件名・Grayscale・「/」は各ラベルのBlack/White設定を使用します。
- 読み込んだ設定の既存サイズは維持します。新しい20µm・3%の初期値は新規作成で最初の画像を追加したときに適用します。

## まずテストする

`Load settings`から`test-data/example-settings.json`を開くと、3条件・Green/Red/Merge・ラベル・10 µmバーが設定されます。

または次の手順を使用します。

1. `Select Images`内の`Open TIFF files...`で`test-data/Control.tif`、`HPR.tif`、`KO.tif`を選択します。またはFijiで画像を開いてからSelect Imagesで追加します。
2. 新規作成時はChannel 1のみ表示します。下の＋からChannel 2とMergeを追加すると3行×3列になります。7条件なら3行×7列です。
3. Condition名とChannel名はテーブルをダブルクリックして編集します。Conditionの`Change source...`は、その条件の全Channelに使用する元TIFを変更します。
4. Channel表で名前・7種類のLUTを編集します。Min/MaxとInvert grayはB&C側で設定します。
5. プレビュー下のB&Cで対象Channelを選び、全条件の画像を見ながら調整します。スライダーはChannelごとに1組で、全条件に共通です。外れ値除去・条件別Autoは行いません。
6. `Style`でラベル、Gap、バーを設定します。左行ラベルは反時計回り、右は時計回りです。
7. 図は自動更新されます。セルをクリックするとB&C対象が切り替わり、下部ステータスにCondition、元TIF、Channel/Mergeの対応を表示します。
8. `Generate TIF`で新しいRGB ImagePlusを表示、`Save RGB TIFF...`で保存します。従来のGenerate Figureと同じ処理です。PNG・PPTXも下部ボタンで保存できます。元TIFへの上書きは禁止しています。

行列数はCondition・表示Channelの数とSwapから自動決定します。旧設定JSONの手動行列情報も読み込めますが、GUIでは実際の構成に合わせて自動行列へ正規化します。空セルは作りません。

`＋ Channel / Merge`では元チャネルをチェックして選びます。表示順はドラッグまたはUp/Down、表示の削除はゴミ箱またはRemoveを使用します。Channel 3を単独表示せずMergeに使用することもできます。Merge対象を変更する場合は既存MergeをRemoveして再追加します。

## スケールと画像の扱い

- 8/16/32-bit grayscaleのXYまたはC×XY TIFFを扱います。Z>1、T>1、RGB入力は拒否します。
- 校正単位はnm、µm/um/micron、mmをµmに変換します。未校正画像ではManual µm/pxを入力します。既存の校正がある場合は手入力値で上書きしません。
- バー長は`round(lengthUm / sourcePixelWidthUm)`です。各条件固有の校正を使います。校正差異はプレビューのステータスと生成時に警告します。
- Every cell / One per condition / Selected cell / Figure onceを選択できます。One per conditionは各条件の最後の表示Channel、Figure onceは右下セルです。
- サイズまたはChannel数が異なる画像は拒否します。中心Crop/Resizeの選択、Z/T投影、セル固有Override、登録済み任意LUT、手動表示倍率変更は未実装です。プレビューは画面サイズに合わせて縮小します。ファイル追加はドラッグ＆ドロップにも対応します。
- 画像部分は元解像度を維持します。長いラベルは自動縮小します。スケールバーが収まらない場合はフォント・余白・長さを調整してください。最終キャンバスは100百万画素を上限としています。表示されるMiB値はRGBバッファ1個分で、処理全体の必要メモリはそれより多くなります。
- プレビューは縮小サンプリングです。微細構造の最終確認はフル解像度の生成画像で行ってください。読込・Auto集計・最終生成は画像サイズに応じて時間がかかります。

## 非破壊設計

`InputImageManager.Source`はChannelのImageProcessorをduplicateした非公開の画素コピーを保持します。レンダラーには元ImagePlusを渡しません。B&CとLUTは読み取った値からRGB出力画素を計算する表示変換です。ROIによる自動切り出しやOverlayの転写は行いません。ラベルとバーは新規RGBキャンバスだけに描画します。

JSONには設定とファイルパスを保存し、画素は保存しません。保存元ファイルのない開画像はJSON保存できません。開画像からの取込時点で元ファイルと画素が異なる場合、JSON再読込ではディスク上のTIFを使用するため、必要な状態を別名TIFへ保存してから取り込んでください。

## ビルドとテスト

JDK 8互換bytecodeを、JDK 21とMaven 3.9.9で生成します。ImageJ/SciJavaはFijiが提供し、Gsonは名前空間を変更してJAR内に同梱します。

```powershell
.\build.ps1
.\generate-test-data.ps1
```

`build.ps1`は既存JAVA_HOMEを優先し、未設定ならこのPCのFiji付属JDKを使用します。作業フォルダの`.tools`にMavenがない場合はPATHの`mvn.cmd`を使用します。他環境ではJAVA_HOMEとMavenを準備してください。標準Mavenでも`mvn verify`でビルドできます。`generate-test-data.ps1`はこのPCのFijiパスを使用します。

ソース・テスト・設定例はGit管理し、生成物（target、dist、artifacts）と依存ツールは除外します。通常は増分ビルドのみを使用します。build.ps1でcleanを指定した場合はtarget配下のリンク／ジャンクションを事前検査し、見つかったら削除処理に進まず停止します。共有依存フォルダへのリンクをビルド出力に置かないでください。

## 構成

| クラス | 責務 |
|---|---|
| FigurePanelBuilderCommand | SciJavaメニュー登録、Swing起動 |
| FigurePanelBuilderDialog / AppearancePanel / ContrastPanel | GUI、共通B&C、横並びプレビュー |
| InputImageManager / Source | 入力と独立した画素コピー、metadata |
| FigureConfiguration / ConditionConfig | 自動Grid、軸、Condition→Source対応 |
| ChannelConfig / DisplayChannel | 全条件共通B&C/LUT、Single/Mergeの参照 |
| ImageRenderer | Channel抽出、表示変換、加算RGB Merge |
| PanelLayoutEngine / LabelRenderer / ScaleBarRenderer | キャンバス、回転ラベル、校正バー |
| PreviewRenderer | 縮小描画、全条件Histogram |
| SettingsSerializer / OutputSafety | JSON往復、元TIF上書き防止 |

実装順とMaven設計は`IMPLEMENTATION_PLAN.md`、実行結果は`VALIDATION.md`を参照してください。
