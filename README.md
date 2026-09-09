# Microscopic figure / Figure Panel Builder

今後の開発は **New Version/** を基準にします。Claude によるレイアウト・デフォルト設定の変更を継承しています。

| 場所 | 内容 |
| --- | --- |
| `New Version/src/` | 最新ソースと自動テスト |
| `New Version/docs/manual/` | PDF 操作マニュアル |
| `New Version/docs/development/` | 仕様・実装計画・検証記録 |
| `New Version/docs/handoff/` | 過去の引継ぎ資料（当時の記録） |
| `New Version/test-data/` | 検証用画像と設定 |
| `New Version/dist/` | 配布用 JAR |
| `New Version/target/`, `artifacts/`, `.tools/` | ビルド出力・検証成果物・依存ツール（Git 管理外） |
| `archive/old-generated/` | 旧版の管理外成果物・ログ・依存ツールを保全（Git 管理外） |

操作・ビルド手順は [New Version/README.md](New%20Version/README.md) を参照。
ルートからの検証: `& '.\New Version\build.ps1'`

削除前の両版は Git コミット `552fd12` に保存済みです。旧ソースはそのコミットの `Old Version/` から参照・復元できます。

画像保存（TIFF/PNG/PPTX）、設定保存、Load settings の初期フォルダは選択中の画像のフォルダです。
選択画像に保存元がなければ図で使用中の画像、次に Fiji の現在の画像を参照します。有効なフォルダがなければ標準のファイル選択先を使用します。
