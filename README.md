# talon-core

Spring Boot BFF for Talon. The browser holds an httpOnly session cookie, never
a Keycloak token. Cluster manifests live in `webb1es/gitops` (`workloads/talon`).

```bash
docker compose up -d          # local Postgres :5432
mvn spring-boot:run           # :9095
```

Export (or set) the vars below. Point `KC_*` at
`https://test-auth.webbies.dev` (realm `talon`) or a local Keycloak.

No Dockerfile / GHCR workflow yet. gitops still pins
`ghcr.io/webb1es/talon-core:bootstrap`.

## Env

| Variable | Purpose |
|---|---|
| `SERVER_PORT` | Default `9095` |
| `SPRING_DATASOURCE_URL` | JDBC URL. Local: `jdbc:postgresql://localhost:5432/talon`. In-cluster: CNPG secret key `jdbc-uri` (not `uri`) |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | Local: `talon` / `password`. In-cluster: CNPG `talon-pg-app` |
| `KC_ISSUER` | `https://<keycloak>/realms/talon` |
| `KC_CORE_CLIENT_ID` / `KC_CORE_CLIENT_SECRET` | Confidential login client `talon-core` |
| `KC_ADMIN_CLIENT_ID` / `KC_ADMIN_CLIENT_SECRET` | Service account `talon-core-admin` |
| `KC_ADMIN_API_BASE` | `{server}/admin/realms/talon` — not `{issuer}/admin` |
| `FRONTEND_URL` | SPA origin (logout redirect + post-login) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated; credentials required, so not `*` |

Realm import (clients, roles, service account — not users) is
`gitops/workloads/keycloak-test/talon-realm.yaml`. Copy generated client secrets
into gitops secret `talon-core-kc`. Logout redirect must exact-match
`FRONTEND_URL + "/login"` in that import.

## Deploy

Bump the image tag in `gitops/workloads/talon/rollout.yaml`. Canary 25→50→75,
Prometheus success-rate ≥ 95%. Sessions are JDBC (2 replicas + traffic split).

## Gotchas

- Confidential client: Spring `oauth2Login` does not send PKCE.
- Roles come from the ID token / userinfo (custom mapper), not the default
  access-token-only `roles` scope.
- CSRF is off; SameSite=Lax is the defense (SPA and API share `mytalon.co.zw`).
- A Keycloak login with no local `users` row is 403, not auto-provisioned.
- `tadmin` is reconciled at boot: missing Keycloak user, missing local row, or
  a mismatched `keycloakId` is created/linked. First password is an app default;
  Keycloak forces a change on first login. No-op if both already match, or if
  Keycloak is down. Staff users are created from the app only — not in the
  realm import.
- Swagger is `super_admin` only.
- Unauthenticated API calls redirect to Keycloak (oauth2Login), they do not 401.
- `/readyz` and `/healthz` check the database and return 503 if it isn't reachable.
- `ddl-auto: update` is on in `application.yml` (including whatever profile gitops runs).
