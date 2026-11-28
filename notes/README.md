# notes

汎用的な技術調査メモを管理するディレクトリです。  
特定のアプリに依存しない技術調査や学習メモを配置します。

## ディレクトリ構成

```
notes/
├── aws/                # AWS での実装メモ
├── azure/              # Azure での実装メモ
├── coding-ai/          # AI コーディングツール・モデル関連
├── dnp/                # 生成 AI の活用事例
├── prompt/             # AI に渡すプロンプトの下書き
├── web-security/       # Web セキュリティ・認証の基礎
└── windows/            # Windows の設定・運用メモ
```

## ドキュメント一覧

### aws/ — AWS での実装

| ドキュメント | 概要 |
|-------------|------|
| [cognito-spa-auth.md](aws/cognito-spa-auth.md) | Cognito を使う SPA（S3 ＋ CloudFront）のトークンの扱いと構成の選び方（Cognito と CloudFront の前提知識・構成ごとのメリットとデメリット・ログイン状態の保持） |
| [bff-and-alb-auth.md](aws/bff-and-alb-auth.md) | Cognito ＋ SPA で BFF・ALB を使う（Lambda の BFF・API サーバーが BFF を兼ねる形・ALB の Cognito 認証・CloudFront で同じオリジンにするときの注意点） |

### azure/ — Azure での実装

| ドキュメント | 概要 |
|-------------|------|
| [easy-auth-token-relay.md](azure/easy-auth-token-relay.md) | App Service の Easy Auth と nginx でトークンを中継する（トークンの扱い方の案の比較・nginx の設定・同じイメージで複数環境・`/.auth/me` を Front Door で遮断） |
| [internal-spa-platform-auth.md](azure/internal-spa-platform-auth.md) | 社内共通 UI ライブラリへの認証の組み込み（Easy Auth ＋ APIM ＋ AKS・アプリ登録の設計・APIM ポリシー・Vue と Axios のライブラリ・移行手順） |

### coding-ai/ — AI コーディングツール・モデル

| ドキュメント | 概要 |
|-------------|------|
| [models-overview.md](coding-ai/models-overview.md) | GitHub Copilot 対応 AI モデル一覧（全モデル横断比較） |
| [models-anthropic-claude.md](coding-ai/models-anthropic-claude.md) | Anthropic Claude モデル比較（Opus / Sonnet / Haiku） |
| [models-openai-gpt.md](coding-ai/models-openai-gpt.md) | OpenAI GPT モデル比較（GPT-4.1 〜 GPT-5.4） |
| [models-google-gemini.md](coding-ai/models-google-gemini.md) | Google Gemini モデル比較（2.5 Pro / 3 Flash / 3.1 Pro） |
| [ai-coding-tools-comparison.md](coding-ai/ai-coding-tools-comparison.md) | AI コーディングツール比較（Copilot / Cursor / Claude Code） |
| [claude-code-meeting-questions.md](coding-ai/claude-code-meeting-questions.md) | Claude Code 導入検討 — AWS営業打ち合わせ質問集 |
| [github-copilot-harness.md](coding-ai/github-copilot-harness.md) | VS Code の Copilot ハーネス調査（Local と Copilot の違い・組み込み GitHub MCP・セキュリティ） |

### dnp/ — 生成 AI の活用事例

| ドキュメント | 概要 |
|-------------|------|
| [生成AIユースケース.md](dnp/生成AIユースケース.md) | GitHub Copilot Agent による Dependabot 対応の自動化の記録 |

### prompt/ — プロンプトの下書き

| ドキュメント | 概要 |
|-------------|------|
| [AppService_アクセストークン.md](prompt/AppService_アクセストークン.md) | App Service（Easy Auth）のトークンを API Management で検証する Vue アプリを、`lab/` に作らせるプロンプト |
| [デザインシステム.md](prompt/デザインシステム.md) | （未記入） |

### web-security/ — Web セキュリティ・認証の基礎

| ドキュメント | 概要 |
|-------------|------|
| [oauth-oidc-basics.md](web-security/oauth-oidc-basics.md) | 前提知識: OAuth 2.0 と OpenID Connect の基礎（トークンの種類・JWT の検証・認可コードフロー ＋ PKCE・公開クライアントと機密クライアント） |
| [origin-cookie-cors-csrf.md](web-security/origin-cookie-cors-csrf.md) | 前提知識: オリジンとサイト・クッキーが送られる条件・CORS・CSRF、BFF や API を同じオリジンに置く理由 |
| [xss.md](web-security/xss.md) | XSS の種類・Vue / React でも起きる書き方・社内アプリでの経路・対策（エスケープ・リンター・CSP・依存の管理） |
| [spa-token-storage.md](web-security/spa-token-storage.md) | SPA のトークンの置き場所（localStorage と sessionStorage の違い・ログイン状態をどこまで保持するか・XSS 以外の漏れ方・IP 制限があるときのリスクの見積もり） |
| [httponly-cookie.md](web-security/httponly-cookie.md) | HttpOnly クッキーとは何か・使える場面と使えない場面・属性・CSRF |
| [spa-auth-architecture.md](web-security/spa-auth-architecture.md) | SPA の認証構成パターン（ブラウザで完結・トークン仲介型・BFF・S3 などの静的ホスティング・API を同じオリジンに置くか分けるか・Azure と AWS での対応） |

### windows/ — Windows の設定・運用

| ドキュメント | 概要 |
|-------------|------|
| [keyboard-layout-per-device.md](windows/keyboard-layout-per-device.md) | 内蔵キーボードと外付けキーボードで配列（JIS / US）を分ける方法 |
| [remote-access-with-tailscale.md](windows/remote-access-with-tailscale.md) | 据え置き PC にスマホ・ノート PC から接続する（Tailscale・電源設定・Pro + RDP・Remote Control の常駐と通知・Windows Update の自動再起動の止め方・方式の比較） |
