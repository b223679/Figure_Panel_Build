# 合成テストデータ

[Releases ページ](https://github.com/b223679/Figure_Panel_Build/releases/latest)の **Assets** から `figure_panel_builder_testset.zip` をダウンロードし、すべて展開して `example-settings.json` を読み込むと試せます。ZIP の更新はプロジェクトルートで `./package-testset.ps1` を実行します。

Image A.tif / Image B.tif / Image C.tifの3枚です。実験データではありません。

- 各256×192、16-bit unsigned、C=3、Z=1、T=1のImageJ hyperstack TIFF
- Channel 1=Green、2=Red、3=Blue
- Pixel width/height=0.25 µm
- 固定seedのノイズ、ガウス状スポット、曲線で構成
- 全条件・全Channelの左上16×16画素は強度1000。共通B&Cが正しければ同じChannelで同じ明るさになります。
- Conditionによってスポット強度を変えています。

example-settings.jsonは3×3のGreen/Red/Merge、共通0–2000、Gap=5、ラベル、10 µmバーの例です。パスはこのJSONを基準とした相対パスです。

ダウンロード手順は[README のクイックスタート](../README.md#サンプルファイルで試す)を参照してください。JSON と TIFF 3 枚を同じフォルダに置いてください。この 4 ファイルはクイックスタートと回帰テストに必要なため保持します。UI スクリーンショットや生成フィギュアは `artifacts/` に出力し、このフォルダには保存しません。

再生成: プロジェクトルートで`generate-test-data.ps1`。生成コードは`src/test/java/org/microscopy/figure/TestImageGenerator.java`です。再生成するとこのフォルダの合成TIFと設定例を更新します。
