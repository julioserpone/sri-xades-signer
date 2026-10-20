# GitHub Copilot instructions

Follow [`AGENTS.md`](../AGENTS.md). This repository signs Ecuadorian SRI electronic documents
with XAdES-BES and deploys the signer as an IAM-protected AWS Lambda.

Key rules:

- Preserve XAdES-BES 1.3.2, RSA-SHA1, SHA-1 references, UTF-8, `SigningCertificate` v1 and signed
  `KeyInfo`.
- Never put certificate material, passwords or secrets in source, tests, logs or docs.
- Never add a public unauthenticated signing route.
- Keep the hardened XML parser, size limits and SRI root allowlist.
- Use DSS 6.5.1 and the existing signing flow; do not replace it with ad-hoc XML signing.
- Add tests and run `mvn -B verify`.
- Do not claim SRI certification or production readiness.
