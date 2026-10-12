# Ravelry PDF download findings

Verified against the user-supplied API notes restored on 2026-10-12. The public API documentation URL redirects to sign-in, so no live account or credentials were used to verify server behavior.

- Basic Auth personal account access grants all permissions; it does not require requesting OAuth scopes. The documentation distinguishes this from read-only Basic Auth.
- POST `/product_attachments/{id}/generate_download_link.json` requires a product attachment ID. `VolumeAttachment.product_attachment_id` is the documented identifier. A volume attachment's own ID must never be substituted.
- OAuth callers need `library-pdf` for a user's library. These tokens expire sooner than ordinary tokens. The documentation specifies rate limiting for link generation; HTTP 429 is distinguished from authentication failure.
- HTTP 403 can mean invalid keys or denied method/resource access. Its status alone does not prove a read-only key, or that OAuth is needed. A successful stash import also does not prove every PDF is available to that account.

Stitchbook logs only the logical request stage and integer HTTP status. URLs, account names, IDs, authorization headers, response bodies, exceptions and signed links are excluded. The same safe diagnostic is shown in the PDF failure message. Authentication, permission denial, rate limiting, server failure, expired signed links and absent direct-download identifiers have distinct remedies. Original PDFs and existing pattern files are never replaced.

If a verified full personal key remains denied at the PDF_LINK stage after product-ID validation, inspect the account's library ownership and current API policy before changing authentication. An optional OAuth flow would use the browser for consent, request library-pdf only when needed, protect callback state, store tokens in the Android Keystore and keep PDF attachment/manual download as an offline-compatible alternative. Public-client PKCE support must be verified before implementation; the supplied notes only announce OAuth 2.1 for Q4 2026. Do not embed a client secret in the app. User approval is required by the standing request before building an OAuth flow.

No claim is made that the user's original phone refusal has been reproduced. The corrected identifier and safe stage/status diagnostics allow the next phone attempt to distinguish its cause without exposing credentials.
