# cloud_server

FreePark backend service. Java 21, Spring Boot 4.1, Spring Data JPA, MySQL, and HTTP I18N.

FreePark 云端后端。使用 Java 21、Spring Boot 4.1、Spring Data JPA、MySQL，并在接口层支持国际化。

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
curl "http://localhost:8080/api/v1/i18n"
curl -H "Accept-Language: zh-CN" "http://localhost:8080/api/v1/i18n"
curl "http://localhost:8080/api/v1/i18n?lang=ja"
```

## MySQL

Start a local database:

```bash
docker compose up -d
```

Default connection:

- host: `localhost:3306`
- database: `freepark`
- user / password: `freepark` / `freepark`

Override with `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, and `MYSQL_PASSWORD`. Do not commit real database passwords; this repository is public.

## Run

Requires JDK 21.

```bash
./mvnw spring-boot:run
```

Windows:

```bat
mvnw.cmd spring-boot:run
```

Health check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

## Test

Tests use an in-memory H2 database (MySQL compatibility mode), so MySQL is not required.

```bash
./mvnw test
```
