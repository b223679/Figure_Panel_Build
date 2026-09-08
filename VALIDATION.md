# 検証結果

## 2026-09-08 最大化・透過・編集可能PPTX

- 最終ビルド成功、27テスト、失敗0・エラー0。
- 1024px画像の初期値: フォント102px、バー長20µm（0.25µm/pxなら80px）、幅31px。既存の手動値・JSON設定は維持。
- 実Swingウィンドウで最大化状態、下部保存ボタンの表示、3択背景ボタン、ラベル選択枠、ヒストグラムのないB&Cを確認。
- PNGの余白alpha=0、画像セルalpha=255。白背景のGreen/Yellow/Cyan、黒背景のBlueのラベル色と画像LUTの独立性を検証。
- PPTX内の元解像度画像、ネイティブ文字・バー図形、mergeの色付き文字、Unicode/XML特殊文字、軸交換、透過時の背景塗り省略を検証。
- 黒背景・背景塗りなしの1枚スライドを構造検査し、Artifact Tool再読込・レンダリング成功。回転行ラベルの回転前外接矩形に境界警告が出るが、描画された文字はスライド内に収まることを確認。
- PowerPointでeditable-figure-validated.pptxを開き、修復警告なしで表示。画像、行列ラベル、バー、バー文字が個別オブジェクトとして認識され、行ラベルのみ選択できることを確認。
- 例: artifacts/editable-figure-validated.pptx、artifacts/transparent-figure.png。合成テスト画像を使用。


## 2026-09-08 画像サイズ連動・Labels / Scale再構成

- ビルド成功、25テスト、失敗0・エラー0。
- 1024px画像でラベル／バー文字102px、バー幅10px、校正0.25µm/pxでバー長25.5µm（102px）を確認。
- 行・列の独立した白／黒設定、旧JSONの共通文字色との互換性、四辺・軸交換時のラベル選択枠、出力画像への枠の非混入を検証。
- AppearanceUiValidationで実ウィンドウの1024px TIFF読込、初期値、ラベル枠、4グループの設定画面を確認。手動フォントサイズ77pxが2枚目の追加・JSON再読込後も保持されることを確認。
- 画面: artifacts/appearance-workspace-1024.png、設定全体: artifacts/appearance-controls.png。

## 2026-09-08 UI更新

- `build.ps1`成功。22テスト、失敗0・エラー0。
- 長いmerge名の自動縮小、ラベルの描画領域、設定フォントサイズの保持を検証。
- 行・列交換時の＋タイルの追加対象、ラベル四辺のヒットテスト、余白とセル間Gapの除外、クリックとドラッグのイベント、古いプレビューへの操作無効化を検証。
- B&CのBrightnessが表示範囲幅を維持し、Contrastが表示範囲中心を維持すること、選択した元チャネルだけを更新すること、LUT変更・Resetを検証。
- `WorkspaceUiValidation`で実Swingウィンドウを表示。＋タイル→チェックボックス選択→3チャネルmerge作成、設定JSON読込、軸交換、B&C開閉、設定パネル表示を検証。
- `artifacts/workspace-*.png`に単独チャネル、merge、7条件、軸交換、B&C非表示、設定パネルの画面を保存。合成画像によるレイアウト検証で、ユーザー添付の顕微鏡写真はテストデータとして取り込んでいません。
- 以前のFiji MCPによるレンダラー検証記録は`artifacts/FIJI-MCP-VALIDATION.md`を参照。

2026-09-07、Windows、Fiji付属Zulu JDK 21.0.7、Maven 3.9.9。

| 段階 | 結果 |
|---|---|
| Phase 1: SciJava skeleton | compile成功 |
| Phase 1: 入力・モデル・レンダラー | compile成功 |
| Phase 1: GUI・RGB出力・コアテスト | verify成功、5 tests |
| Phase 2: ラベル・Gap・スケールバー | verify成功、8 tests |
| Phase 3: Preview・JSON・Histogram・UI | verify成功、11 tests |
| 最終: 非破壊・UI連動の追加検証 | verify成功、16 tests、失敗0・エラー0 |

ログ: phase1-verify.log、phase2-verify.log、phase3-verify.log、final-verify.log。JUnit詳細: target/surefire-reports。

## 確認した内容

- 7条件×Green/Red/Merge→3×7、軸交換、手動不一致拒否
- 共通画素1000の出力RGB一致、additive Merge、7 LUT、invert、clamp
- 全条件のAuto min/maxとHistogramの画素総数
- 全Channel元画素、LUT、表示範囲、ROI、Overlay、Channel位置、校正の保持
- 元画像変更後も取り込んだコピーが独立していること
- 処理前後で元TIFFのSHA-256一致
- 開画像の元パスを用いた保存先保護、設定保存による元TIF上書き拒否
- Z/T拒否、サイズ不一致、Channel参照不整合、非有限B&C、設定version不整合
- 校正µm/nm、未校正時の手入力、バー適用範囲、セルに入らないバー拒否
- ラベル領域・Gap寸法、左右上下配置
- 3枚のTIFF読込、C=3/Z=1/T=1/16-bit確認、JSON往復、24-bit RGB TIFF再読込
- SwingスライダーがChannel共通設定だけを変更すること、Gapの連動
- SciJavaのMETA-INF/json/org.scijava.plugin.Pluginにメニュー登録生成を確認
- artifacts/example-figure.pngの3×3配置・ラベル・バーを目視確認
- Swing UIのオフスクリーン描画確認。実際のFijiメニュー起動・ファイルダイアログ・ドラッグ操作の手動E2Eテストは未実施

実装の対象外と操作上の制約はREADME.mdに記載。
