# Fiji Figure Panel Builder

Condition × DisplayChannelから、元画像を変更せずRGB TIFF・PNG・編集可能なPPTXのfigureを作成するSciJava/Swingプラグインです。

## Fijiへの導入

1. `dist/figure-panel-builder-1.0.0.jar`をFijiの`plugins`フォルダにコピーします。
2. Fijiを再起動します。
3. `Plugins > Figure Panel Builder`を開きます。

Windows上のFiji付属JDK 21、ImageJ 1.54p、SciJava 2.100.1で検証しています。2026-09-08版は、プレビュー中心のUI、＋タイル、ラベル編集、ドラッグ並べ替え、プレビュー下の共通B&Cを追加しています。

## プレビューから操作する

- 新規作成では最初の画像の長辺に応じて初期サイズを設定します。行・列ラベルとスケールバー文字は約10％（1024pxなら102px）、バー線幅は約3％（1024pxなら31px）、余白は約2％です。バー長の初期値は20µmです。未校正の場合はManual µm/pxの設定が必要です。読み込んだ設定や、初期値から手動変更した値は維持します。
- ラベルをクリックすると、そのラベル領域に青い選択枠が表示されます。条件名・チャネル名のどちらでも利用でき、枠は出力画像には含まれません。
- Labels / ScaleはBackground、Gap、Labels、Scale barの順です。白／黒や上下左右をトグルで選択し、行・列ラベルとバーの表示をOFFにすると詳細設定が折りたたまれます。スケールバーのLocationには位置・余白・適用範囲をまとめています。

- 新規作成は最初のチャネル1行から始まります。右の「＋ Condition」でTIFF条件を追加し、下の「＋ Channel / Merge」で表示するチャネルを追加します。複数のチェックを入れるとmergeになります。
- 「Swap rows / columns」で軸を交換すると、＋タイルの追加対象も入れ替わります。
- 画像をクリックすると、そのチャネルのB&Cを下に表示します。mergeではChannel欄から調整する元チャネルを選びます。Min / Max / Brightness / Contrast、LUT変更は全条件共通でライブ反映されます。
- Autoは全条件の有限画素の最小値・最大値を使用します。Resetは8-bitで0–255、16-bitで0–65535、32-bitで全条件のデータ範囲に戻します。元画像の画素は変更しません。
- チャネルラベルをダブルクリックすると名前とLUTを編集できます。mergeでは構成チャネルごとの名前・LUTをまとめて編集します。表示は各LUT色の名前を「/」で結合します。条件ラベルもダブルクリックで編集できます。
- 長いラベルは、設定したフォントサイズを上限に文字だけ縮めてセルに収めます。画像サイズや保存したフォント設定は変えません。
- 画像を横方向にドラッグすると列を、縦方向にドラッグすると行を移動します。条件単位・表示チャネル単位の並べ替えです。
- 「B&C」で調整パネルを開閉できます。「Settings / Sources」から条件の削除・ソース変更、チャネルの削除、ラベル位置、Gap、スケールバーなどを設定します。
- ＋タイル、操作説明、選択枠はUIだけに表示し、Generate FigureやTIFFには含めません。

## PNG・PPTX出力

- 起動時にタスクバーを除く画面領域で最大化します。B&Cにはヒストグラムを表示しません。
- BackgroundからWhite / Black / Transparentを選びます。透過部分はプレビューだけ市松模様で表示し、`Save PNG...`ではアルファ透明度を保存します。画像セルそのものは不透明です。
- `Save PPTX...`は図と同じ縦横比の1枚のスライドに、各セルの元解像度PNG・行列ラベル・スケールバー・バー文字を別オブジェクトで配置します。PowerPointで個別に移動・文字編集できます。Mergeラベルは1つのテキストボックス内で各チャネル名を色分けします。
- PPTXのTransparentは背景の塗りを設定しません。PowerPointのスライド用紙は通常白く表示されますが、画像・ラベルを別のスライドへ移動すると余白の背景は付きません。透明なラスター画像が必要ならPNGを選びます。
- Generate Figure / RGB TIFFは透明度を保存できないため、透過設定時はPNGまたはPPTXへ案内します。
- 白背景のYellow/Cyan/Greenラベルは暗め、黒背景のBlueラベルは明るめに補正します。画像のLUTは変更しません。透過時のラベル色は元のLUT色です。条件名・Grayscale・「/」は各ラベルのBlack/White設定を使用します。
- 読み込んだ設定の既存サイズは維持します。新しい20µm・3%の初期値は新規作成で最初の画像を追加したときに適用します。

## まずテストする

`Load settings`から`test-data/example-settings.json`を開くと、3条件・Green/Red/Merge・ラベル・10 µmバーが設定されます。

または次の手順を使用します。

1. `Add TIF...`で`test-data/Control.tif`、`HPR.tif`、`KO.tif`を選択します。順序はUp/Downで変更できます。
2. Automatic gridはONのままにします。新規作成時はChannel 1のみ表示します。下の＋からChannel 2とMergeを追加すると3行×3列になります。7条件なら3行×7列です。
3. Condition名とChannel名はテーブルをダブルクリックして編集します。Conditionの`Change source...`は、その条件の全Channelに使用する元TIFを変更します。
4. Channelテーブルで共通Min/Max、7種類のLUT、Grayscale invertを設定します。
5. プレビュー下のB&Cで対象Channelを選び、全条件の画像を見ながら調整します。スライダーはChannelごとに1組で、全条件に共通です。外れ値除去・条件別Autoは行いません。
6. `Labels / Scale`でラベル、Gap、バーを設定します。左行ラベルは反時計回り、右は時計回りです。
7. `Preview`または自動更新で図を確認します。セルをクリックするとB&C対象が切り替わり、下部ステータスにCondition、元TIF、Channel/Mergeの対応を表示します。
8. `Generate Figure`で新しいRGB ImagePlusを表示、`Save RGB TIFF...`で保存します。元TIFへの上書きは禁止しています。

`Rows = Channels`をOFFにするとRows=Conditions、Columns=Channelsになります。Automatic gridをOFFにした場合は手動行列数が条件数・表示Channel数と一致する必要があります。空セルは作りません。

`Add channel / Merge`では元チャネルをチェックして選びます。表示順はドラッグまたはUp/Down、表示の削除はRemove、名前と色の編集はEdit label / LUTを使用します。Channel 3を単独表示せずMergeに使用することもできます。Merge対象を変更する場合は既存MergeをRemoveして再追加します。

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
