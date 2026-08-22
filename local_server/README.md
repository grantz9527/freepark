# local_server

FreePark on-premise backend. Java 21, Spring Boot 4.1, Spring Data JPA, MySQL, and HTTP I18N.

FreePark 本地/场端后端。使用 Java 21、Spring Boot 4.1、Spring Data JPA、MySQL，并在接口层支持国际化。

Default port is **8081** so it can run next to `cloud_server` (8080).

默认端口为 **8081**，可与 `cloud_server`（8080）同时运行。

## Stack

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA (Hibernate)
- MySQL 8.4
- Message bundles under `src/main/resources/i18n/`

## I18N

Locale is resolved in this order:

1. Query parameter `lang`, for example `?lang=zh-CN`
2. HTTP header `Accept-Language`
3. Fallback: `en`

Supported locales: `en`, `zh-CN`, `zh-TW`, `ja`, `ko`, `es`, `fr`, `de`, `pt`, `ar`.

Check it:

```bash
curl "http://localhost:8081/api/v1/i18n"
curl -H "Accept-Language: zh-CN" "http://localhost:8081/api/v1/i18n"
curl "http://localhost:8081/api/v1/i18n?lang=ja"
```

## MySQL

Start a local database:

```bash
docker compose up -d
```

Default connection:

- host: `localhost:3307`
- database: `freepark_local`
- user / password: `freepark_local` / `freepark_local`

Override with `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, and `MYSQL_PASSWORD`.

## Run

Requires JDK 21.

```bash
./mvnw spring-boot:run
```

Windows:

```bat
mvnw.cmd spring-boot:run
```

Health check: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)

## Login

On first startup, a default admin is created if the user table is empty:

- username: `admin`
- password: `admin123`

Override with `FREEPARK_ADMIN_USERNAME` and `FREEPARK_ADMIN_PASSWORD`. Change this password in production.

```bash
curl -X POST "http://localhost:8081/api/v1/auth/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

Use the returned Bearer token for authenticated APIs such as `GET /api/v1/auth/me`.

## Test

Tests use an in-memory H2 database (MySQL compatibility mode), so MySQL is not required.

```bash
./mvnw test
```
