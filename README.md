# market

A REST API for a small online market, built with Spring Boot, Spring Security (JWT), JPA and PostgreSQL.

- **Users** sign up and receive a one-time code (OTP) by email (and optionally SMS through Twilio). After verifying it they can sign in and get a JWT.
- **Admins** add items with a price and an optional image. Prices are kept as history: adding a new price row keeps old orders pointing at the price they were placed with.
- **Users** browse items, view item images, and place orders. An order stores the delivery location, item quantities, and a delivery charge, and the response includes the total.

A hand-drawn sketch of the intended data model is in [docs/data-model-sketch.jpeg](docs/data-model-sketch.jpeg).

## Running it

Requirements: Java 11+ (17 recommended) and PostgreSQL.

```bash
./gradlew bootRun
```

`bootRun` supplies a throwaway `JWT_SECRET` for local use. A packaged jar has no default and refuses to start without one.

By default the app connects to `jdbc:postgresql://localhost:5433/market` with user/password `market`. For example:

```bash
docker run -d --name market-db -p 5433:5432 -e POSTGRES_DB=market -e POSTGRES_USER=market -e POSTGRES_PASSWORD=market postgres:16
```

API docs (Swagger UI) are at <http://localhost:8080/swagger-ui.html>.

### Configuration

All settings come from environment variables; none of the secrets have real defaults.

| Variable | Purpose | Default |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection | local `market` database |
| `JWT_SECRET` | JWT signing key, at least 32 bytes | **required** (`./gradlew bootRun` sets a local-only value) |
| `JWT_EXPIRATION_MS` | Token lifetime | 1 day |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_EMAIL` | Creates an admin account at startup if it doesn't exist | unset (no admin) |
| `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Sending OTP emails | disabled, Gmail SMTP, no credentials |
| `SMS_ENABLED`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_NUMBER` | Sending OTP SMS | disabled |
| `OTP_EXPIRATION_MS` | OTP lifetime | 10 minutes |
| `DELIVERY_CHARGE` | Delivery charge added to each order | `2.00` |
| `SIGNUP_MAX_PER_HOUR` | Sign-ups allowed per client address per hour | `10` |
| `API_DOCS_ENABLED` | Publish `/v3/api-docs` and Swagger UI | `true` — **set `false` in production** |
| `TRUSTED_PROXIES` | Regex of proxy addresses whose `X-Forwarded-For` is trusted | loopback only |
| `DB_CONNECTION_INIT_SQL` | SQL run on each new DB connection | `SET lock_timeout = '3s'` |

Email is off by default. Set `MAIL_ENABLED=true` with SMTP credentials to send OTPs; otherwise read the OTP from the database (`user_otp` table).

Sign-in is blocked for 15 minutes after 5 wrong passwords in a row for the same username from the same IP address, so an attacker can't lock the real user out. One IP address making 500 failed sign-ins (across any usernames) is blocked for 15 minutes too, except for users who have signed in successfully from it before. The client IP is read from `X-Forwarded-For` only when the request comes from a trusted proxy. By default that is only a proxy on the same host; behind a load balancer, set `TRUSTED_PROXIES` to a regular expression matching its addresses (for example `10\.0\.\d+\.\d+`). Don't trust a whole private range unless only your proxies can reach the app: with Docker port publishing, for instance, every client arrives from a private gateway address and could then fake its IP.

**Single instance only:** these counters are kept in memory, so they reset on restart and aren't shared between instances. Before running several instances, move them to a shared store (a database table or Redis).

An OTP stops working after 5 wrong guesses. A new one can be requested at most once a minute and 5 times a day; `resend_otp` gives the same answer whether or not a code was sent, so it doesn't reveal which accounts exist. Sign-up does say when an email or username is taken, so it is limited per client address (`SIGNUP_MAX_PER_HOUR`). An unverified account whose OTP has expired is removed when someone signs up again with the same username or email.

Changing the password signs out every existing session (older tokens stop working).

Usernames and emails are case-insensitive (stored lower-case). Item names are unique regardless of case.

Changing `ADMIN_PASSWORD` and restarting updates the existing admin's password and signs out its sessions. If the admin changed their own password in the app, a restart with the same `ADMIN_PASSWORD` keeps it.

### Database schema

The schema is managed by [Flyway](https://flywaydb.org/) migrations in `src/main/resources/db/migration`, applied automatically at startup. Hibernate only validates that the entities match. To change the schema, add a new `V<n>__description.sql` file; never edit one that has already run.

A database created by an older version of this app (which let Hibernate create tables) has no Flyway history, and Flyway refuses to start on it. Drop and recreate that database once. Older versions recreated the schema on every start, so there is no data worth keeping in it.

## API

Every JSON response has the shape `{ "status": "OK", "payload": ..., "errors": ... }`. Authenticated calls need `Authorization: Bearer <token>`.

| Method | Path | Who | Body |
| --- | --- | --- | --- |
| POST | `/api/user/signup` | anyone | `username` (3-50 letters, digits, `.`, `_`, `-`), `email`, `password` (8+ chars), `firstName`, `lastName`, `mobileNumber` (international format, e.g. `+96170123456`) |
| POST | `/api/user/verify` | anyone | `userId`, `otp` |
| POST | `/api/user/resend_otp` | anyone | `userId` |
| POST | `/api/user/signin` | anyone | `username`, `password` → `token` |
| POST | `/api/user/change_password` | signed in | `oldPassword`, `newPassword` → new `token` (older tokens stop working) |
| GET | `/api/item/all?page=0&size=20` | user / admin | — |
| GET | `/api/item/image?itemId=1` | user / admin | — (returns the image) |
| POST | `/api/item/new` | admin | multipart: `itemName`, `amount`, optional `image` (PNG, JPEG, GIF or WebP) |
| POST | `/api/order/place` | user | `locationLatitude`, `locationLongitude`, `items` (`{ "<itemId>": <qty> }`, up to 100 items) |

## Tests

```bash
./gradlew test
```

The tests run the full flow (sign up → verify → sign in → add item → order) against an in-memory H2 database, so no PostgreSQL is needed.
