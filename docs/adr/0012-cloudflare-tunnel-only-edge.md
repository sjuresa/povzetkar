# 12. Cloudflare Tunnel as the Only Edge — HTTP Concerns in the Application

Date: 2026-09-29

## Status

Accepted (POV-214). Reverses the direction noted in POV-210 ("Caddy as the HTTP layer"). Extended by POV-221
(see Update).

## Context

Caddy was the public entry point of the OCI deployment: it terminated TLS (ACME), redirected HTTP to HTTPS,
compressed responses, routed `povzetkar.<domain>` and `grafana.<domain>`, and added the security headers
(HSTS, `X-Frame-Options`, `nosniff`, …). Production later moved to a local Docker stack behind a
**Cloudflare Tunnel**, and the tunnel was configured to route straight to `app:8080`. Caddy kept running but saw
no production traffic: no security header reached a client, its ACME challenges failed on every start, and it
still published host ports 80/443 — a path around the tunnel. `X-Forwarded-*` headers were honoured from any
peer.

Every other role of Caddy is covered by the edge that exists: TLS, certificates and "Always Use HTTPS" by
Cloudflare; the public entry by `cloudflared`, which connects outward (no open host port); compression by
Cloudflare towards the client; per-hostname routing by the tunnel configuration. When the app is down, the
Docker host usually is too, and a maintenance page is a Cloudflare Worker. On this Cloudflare plan no request
logs are kept, so the application's access log is the forensic record either way.

## Decision

- **The Cloudflare Tunnel is the only edge.** `cloudflared` routes to `app:8080`; there is no reverse proxy and
  no host port for public traffic. Caddy is removed (compose service, volumes, `infra/caddy/`, CI validation).
- **The application owns the HTTP concerns**, all configured in `application.properties`:
  - Security headers on every response: HSTS (`max-age=31536000; includeSubDomains`), `X-Frame-Options: DENY`,
    `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`.
  - An enforced **Content-Security-Policy** in two policies with mutually exclusive path regexes: a strict one for
    the SPA and everything else (`script-src 'self'` plus the sha256 of the inline pre-paint script; no
    `'unsafe-eval'`) and a legacy one for the server-rendered UI (`/ui/*`, `/login`, `/logout`) that allows its
    inline code and CDN styles until that UI is removed.
  - **Caching:** only Vite's content-hashed `/assets/*` is `immutable`; everything else static is revalidated.
  - **Proxy trust:** `X-Forwarded-*` only from `cloudflared`'s fixed address (fixed subnet in the prod compose);
    the default in every profile is loopback only.
  - **Access log** in its own rotated file, with Cloudflare's client IP, country and ray ID and the authenticated
    principal.
- **Grafana has no public hostname** (`127.0.0.1:3000`; remotely via SSH port forwarding, or later a tunnel
  hostname behind Cloudflare Access).

## Consequences

**Positive:**
- Headers, CSP, caching, proxy trust and the access log are asserted in the test suite (`SecurityHeadersTest`,
  `CachingHeadersTest`, `CspInlineScriptHashTest`, `TrustedProxyTest`, `AccessLogTest`, Playwright project
  `csp`), which the Caddy variant could only do with Docker in CI.
- One component and one trust hop fewer; no open host port; the stack moves to another Docker host without
  opening ports (the fixed subnet `10.203.0.0/24` lies outside Docker's default pools; check it is free there).
- The access log can name the user, which a proxy cannot.

**Negative:**
- Cloudflare is the only way in: moving off it means building a public entry again (TLS, headers stay in the app).
- The legacy UI's CSP is weak (`'unsafe-inline'` scripts) until it is removed.
- Every change to the inline pre-paint script needs a new hash (a test names the mismatch). `frontend/index.html`
  is checked out with LF on every platform (`.gitattributes`), or the hash would depend on the checkout.
- A change of the network's IPAM settings needs a full stack restart (RUNBOOK §10.4a).

**Rejected alternatives:**
- **Caddy on the traffic path** (tunnel → `caddy:80` → app): two trust hops (a mistake breaks the OIDC redirect
  URI), header delivery testable only with Docker in CI, another image to patch — for headers the app can send.
- **Cloudflare Transform Rules** for the headers: configuration outside git, untested, invisible in reviews.

## Update (POV-221)

The edge was hardened further on the same principle (the app owns the HTTP concerns):

- **Allow-list SPA fallback:** `SpaRoutingHandler` serves `index.html` only for the frontend's routes — the exact
  URL patterns of `frontend/src/router.tsx` (`SPA_ROUTES`, `*` per path parameter; `SpaRouteAllowListTest` fails
  unless the two sets are equal); any other path that reaches it answers a bare `404`, so probes — also under a
  route prefix, e.g. `/admin/does-not-exist` — are visible as 404 in the access log and metrics. Unknown IDs in a
  parameter (`/sources/<unknown>`) still get the shell; the view shows the error.
- **No CORS:** every browser client is same-origin (production through the tunnel, dev through the Vite proxy).
  `CorsPreflightHandler`, `OriginValidator`, `povzetkar.cors.*` and `FRONTEND_URL` are removed; login/logout
  redirects are relative. A frontend on a separate origin would need CORS and absolute redirects again.
- **401 for unauthenticated XHR:** `java-script-auto-redirect=false` with a `JavaScriptRequestChecker`
  (`XhrAuthenticationChallenge`) answers `X-Requested-With` requests with `401`, independent of `Origin`; browser
  navigations keep the 302 to Google.
- **Bounded `uri` metric tag:** `HttpMetricsConfig` is an allow-list — it keeps endpoint templates, Quarkus'
  special values and the fixed login routes, and collapses every other path into `SPA`/`STATIC`/`UNKNOWN` (the tag
  carries the raw path, so raw variants of an SPA route must not survive); `suppress4xx-errors` turns untemplated
  4xx into `UNKNOWN`. Traffic cannot exhaust Micrometer's 100-value limit.
