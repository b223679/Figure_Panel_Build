# 実装計画

仕様書.mdとユーザー追加要件を優先する。Java 8 bytecode、Maven、ImageJ 1 API、SciJava Command、Swing。Fiji付属JDK 21で検証する。

## Phase 1
1. Maven/SciJava skeletonをコンパイル。
2. InputImageManager: TIFと開いているImagePlusからprivate pixel snapshot。C×XYのみ、RGB入力とZ/T stackは拒否。
3. ConditionConfig → source ID、ChannelConfig → 共通Min/Max/LUT、DisplayChannel → channel IDまたはMerge。
4. ImageRenderer: clamp display mappingとadditive RGB。入力にsetterを呼ばない。
5. PanelLayoutEngine: 自動gridを既定、軸交換、手動grid検証。
6. Swing GUI、Generate、RGB TIFF保存。JUnitで非破壊性と共通B&C、Mergeを検証。

## Phase 2
LabelConfig / LabelRenderer、ScaleBarConfig / ScaleBarRenderer、gap。元解像度を保持。寸法不一致は安全なCancelとして拒否し、resize/crop選択はv1対象外。校正差異を警告し各セル固有の校正で描く。

## Phase 3
PreviewRenderer、全condition横並びB&Cスライダー、histogram、SettingsSerializer JSON、Up/Down並べ替え、ファイルdrop。各Phaseでmvn verifyを実行してから次に進む。

## Maven構成
src/main/java/org/microscopy/figure、src/main/resources、src/test/java/org/microscopy/figure。ImageJ/SciJavaはprovided、Gsonは衝突防止のためshade/relocate、JUnitはtest scope。

## 検証データ
test-data/*.tif: synthetic 16-bit、256×192、C=3 Z=1 T=1、3 conditions、0.25 µm/pixel。再生成コードと期待値テストを保存。実測データではない。

## 参考
SciJava登録: https://imagej.net/develop/plugins
Maven: https://imagej.net/develop/maven-faq
