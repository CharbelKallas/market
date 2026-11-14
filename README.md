# market

A REST API for a small online market, built with Spring Boot, Spring Security (JWT), JPA and PostgreSQL.

- **Users** sign up and receive a one-time code (OTP) by email (and optionally SMS through Twilio). After verifying it they can sign in and get a JWT.
- **Admins** add items with a price and an optional image. Prices are kept as history: adding a new price row keeps old orders pointing at the price they were placed with.
- **Users** browse items, view item images, and place orders. An order stores the delivery location, item quantities, and a delivery charge, and the response includes the total.

A hand-drawn sketch of the intended data model is in [docs/data-model-sketch.jpeg](docs/data-model-sketch.jpeg).

## Running it

Requirements: Java 11+ (17 recommended) and PostgreSQL.

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

The `dev` profile supplies a local-only `JWT_SECRET`. Without it (or a real `JWT_SECRET`) the app refuses to start.

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
| `DDL_AUTO` | Hibernate schema mode | `update` |
| `JWT_SECRET` | JWT signing key, at least 32 bytes | **required** (the `dev` profile sets a local-only value) |
| `JWT_EXPIRATION_MS` | Token lifetime | 1 day |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_EMAIL` | Creates an admin account at startup if it doesn't exist | unset (no admin) |
| `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Sending OTP emails | disabled, Gmail SMTP, no credentials |
| `SMS_ENABLED`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_NUMBER` | Sending OTP SMS | disabled |
| `OTP_EXPIRATION_MS` | OTP lifetime | 10 minutes |
| `DELIVERY_CHARGE` | Delivery charge added to each order | `2.00` |

Email is off by default. Set `MAIL_ENABLED=true` with SMTP credentials to send OTPs; otherwise read the OTP from the database (`user_otp` table).

An OTP stops working after 5 wrong guesses, and a new one can be requested at most once a minute.

### Upgrading an existing database

`DDL_AUTO=update` adds new tables and columns but never changes or drops existing ones. A database created by an older version of this app (before item images moved to their own `item_image` table, prices became `numeric`, and order coordinates became numbers) must be dropped and recreated once, for example by running with `DDL_AUTO=create` one time. Older versions recreated the schema on every start, so there is no data worth keeping in it.

## API

Every JSON response has the shape `{ "status": "OK", "payload": ..., "errors": ... }`. Authenticated calls need `Authorization: Bearer <token>`.

| Method | Path | Who | Body |
| --- | --- | --- | --- |
| POST | `/api/user/signup` | anyone | `username`, `email`, `password` (8+ chars), `firstName`, `lastName`, `mobileNumber` |
| POST | `/api/user/verify` | anyone | `userId`, `otp` |
| POST | `/api/user/resend_otp` | anyone | `userId` |
| POST | `/api/user/signin` | anyone | `username`, `password` → `token` |
| POST | `/api/user/change_password` | signed in | `oldPassword`, `newPassword` |
| GET | `/api/item/all?page=0&size=20` | user / admin | — |
| GET | `/api/item/image?itemId=1` | user / admin | — (returns the image) |
| POST | `/api/item/new` | admin | multipart: `itemName`, `amount`, optional `image` (PNG, JPEG, GIF or WebP) |
| POST | `/api/order/place` | user | `locationLatitude`, `locationLongitude`, `items` (`{ "<itemId>": <qty> }`) |

## Tests

```bash
./gradlew test
```

The tests run the full flow (sign up → verify → sign in → add item → order) against an in-memory H2 database, so no PostgreSQL is needed.
