# 検証結果

## 2026-09-08 Figure直接操作・Undo/Redo

- Gitは初期化済み・コミットなしだったため、既存ソース／設定例をbaseline `f63f429`として保存。既存ファイルのreset/revert/deleteは行っていない。履歴管理を`c741d3b`、主要UI変更を`a13dd1f`で段階的にコミット。
- 前回の補助ライブラリ復旧結果を確認。同一版26.905.11957の公式アーカイブと7,796ファイルの照合・Artifact Tool import成功を確認済み（artifacts/runtime-recovery/restoration.json）。今回の作業では増分ビルドだけを使用。build.ps1にはclean前のリンク／ジャンクション検査を追加。
- 最終ビルド成功、JUnit 30テスト、失敗0・エラー0。履歴の独立性・連続編集の集約・Undo後の分岐・空レイアウトと初期化状態の復元を追加検証。
- `DirectManipulationValidation`を既存Fijiとは別のJVMで実行。合成Control/HPR/KO画像を使用し、以下を実Swingウィンドウで確認。

| 確認内容 | 結果 |
|---|---|
| 起動直後のSelect Images、上部Select Images、＋Condition | 同一選択UIで画像追加成功、キャンセル成功 |
| ＋Channel / Merge | 単独追加・3チャネルmerge追加成功 |
| Condition / Channel並べ替え、Swap | ドラッグと両軸の対応を確認 |
| ドラッグ表示 | 対象の半透明＋枠、挿入先ライン、ゴミ箱hoverの強調を目視確認 |
| ゴミ箱Drop | 通常配置・軸交換後のCondition/Channel/Merge除外、確認・キャンセル成功 |
| 全Condition削除後 | Channel表の選択でB&Cのnull参照が起きないこと、Undoで最後のConditionが復元されることを確認 |
| Undo / Redo | Ctrl+Z/Ctrl+Yの登録キーからActionを起動し、追加・削除・名前・B&Cなどを復元。上部アイコンも同じ処理を使用 |
| 名前・LUT・B&C・Invert gray | 元チャネルの共通設定に反映し、mergeの名前も追随 |
| Label / Scale bar | Style画面で変更し、Undo/Redo・設定再読込後も保持 |
| Generate TIF | 従来と同じ24-bit RGB ImagePlusを生成 |
| Save RGB TIFF / PNG / PPTX | 実際の保存ボタンとファイル選択画面から保存成功 |
| Save settings / Load settings | 相対ソースパスを含むJSON保存・再読込成功、構成とStyleを保持 |
| 元TIFの保護 | 3ファイルが残存し、操作前後のSHA-256一致 |
| 右ペインと画面サイズ | Label nameの外側スクロール不要。最大化・1280×800で両表と操作ボタン、B&C、ゴミ箱の収まりを確認 |

最終実行出力: `artifacts/direct-ui-5112190191842314320/`。画面、ドラッグ中の画像、TIFF/PNG/PPTX、設定JSONを保存。テストはアプリ内のイベントとキー割当経路を使用し、ユーザーの既存Fijiウィンドウには接続していない。

主な変更ファイル:

- `FigurePanelBuilderDialog.java`: 共通画像選択、起動時選択、表／タブ／ツールバー整理、削除確認、履歴UI連携。
- `FigureWorkspace.java`: Figure内Swap、ドラッグ対象と挿入先表示、ゴミ箱Drop、Escキャンセル。
- `ContrastPanel.java`: Invert grayと履歴に使う対象チャネル情報。
- `FigureHistory.java`: 最大100操作の設定履歴、不変の画像スナップショットの共有。
- `FigureIcons.java` / `TrashTarget.java`: Swap・Undo/Redo・ゴミ箱のアイコンとDrop強調。
- `FigureHistoryTest.java` / `DirectManipulationValidation.java` / 既存UI検証2ファイル: 履歴・操作・保存の確認。
- `.gitignore` / `AGENTS.md` / `build.ps1` / `README.md` / `VALIDATION.md`: Git運用、削除事故防止、操作説明と検証記録。

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

## 2026-09-09: Claude 版を基準に整理・保存先を修正
- 削除前の Old Version と New Version のソース・文書を `552fd12` に保存し、Old Version の追跡対象が HEAD と一致することを削除前に確認。
- 管理外の旧成果物・ログ・依存ツールはルートの `archive/old-generated/` に保全。New Version の以前の配布物は `archive/previous-new-dist/` に保全。
- Claude のレイアウト・初期値・LUT・Inset の変更を継承。今回の製品コード変更はファイル選択の初期フォルダに限定。
- 通常モード / Free build: 選択画像 → 図の使用画像 → Fiji の現在画像 → 標準フォルダの順に解決。TIFF / PNG / PPTX / 設定保存 / 設定読み込みに適用。
- `build.ps1` verify: 46 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS。
- 新規回帰テスト: 選択画像の優先順位、無効な親フォルダのフォールバック、未保存画像。
- 実際のネイティブファイルダイアログの手動操作は未実施。既存 Fiji プロセスおよび入力画像は変更していない。
- 最新 JAR: `New Version/dist/figure-panel-builder-1.0.0.jar`（リポジトリルートから）。
