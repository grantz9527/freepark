# Deploy FreePark (agent procedure)

Execute these steps in order. Stop if a required tool is missing. Do not skip health checks.

**Pick one OS tree and stay in it:**

- Windows → `ai-build/windows/` (`*.ps1` only)
- Linux or macOS → `ai-build/linux/` (`*.sh` only)

Do not mix the two trees.

Required on the host:

- Docker Engine + `docker compose` (Windows/Mac: Docker Desktop)
- JDK 21 (for `local_server/mvnw`)
- Node.js `^22.18.0 || >=24.12.0` and `npm`

Optional: cameras / Frigate / HyperLPR3 — see §7. Default deploy is **console only** (MySQL + Mosquitto + `local_server` + `local_frontend`).

Do not use `local_server/src/main/resources/application-dev.yml` (different MySQL port/password). Default `application.yml` expects MySQL at `localhost:3307`.

## 0. Repo root

All commands below assume the git repository root (the directory that contains `ai-build/`, `local_server/`, `local_frontend/`).

## 1. Docker dependencies

Windows:

```powershell
powershell -File ai-build/windows/up.ps1
```

Linux / macOS:

```sh
sh ai-build/linux/up.sh
```

Wait until the script prints `OK`. Confirm:

- `http://127.0.0.1:3307` — MySQL (container `freepark-local-mysql`)
- `127.0.0.1:1883` — MQTT (container `freepark-mosquitto`, user `freepark` / `freepark`)

If Mosquitto is unhealthy, the usual cause is a missing `docker/mosquitto/config/pwfile`. The up script generates it. Do not commit `pwfile`.

## 2. Java drivers + frontend packages

Windows:

```powershell
powershell -File ai-build/windows/install.ps1
```

Linux / macOS:

```sh
sh ai-build/linux/install.sh
```

This installs `driver-api` and `zhensi-driver` into the local Maven repo (required — `local_server` is not a multi-module reactor), then `npm install` in `local_frontend`.

## 3. Backend (port 8081)

Run in the background. Do not use `-Dspring-boot.run.profiles=dev`.

Windows:

```bat
cd local_server
mvnw.cmd -DskipTests spring-boot:run
```

Linux / macOS:

```sh
cd local_server
./mvnw -DskipTests spring-boot:run
```

Poll until this returns HTTP 200:

```
GET http://127.0.0.1:8081/actuator/health
```

Timeout: 2 minutes. If it fails, read the Maven log (almost always: MySQL not up, or driver jars not installed).

## 4. Frontend (port 5173)

Run in the background **after** the backend health check passes. Vite proxies `/api` to `8081`.

```sh
cd local_frontend
npm run dev
```

If 5173 is busy, Vite binds 5174, 5175, … (`strictPort` is off). Use the URL printed as `Local:`.

## 5. Verify

Windows:

```powershell
powershell -File ai-build/windows/verify.ps1
```

Linux / macOS:

```sh
sh ai-build/linux/verify.sh
```

Success means:

- `GET /actuator/health` is 200
- `POST /api/v1/auth/login` with `{"username":"admin","password":"admin123"}` returns a token

Console: **http://localhost:5173** (or the Vite port). Default login: `admin` / `admin123`.

## 6. What not to do

- Do not `docker compose` Frigate by default. `docker/frigate/config/config.yml` points at site-specific RTSP URLs.
- Do not put video on MQTT. MQTT is control/state only.
- Do not change i18n locales beyond zh-CN / zh-TW / en when editing product copy.
- Do not implement backend features unless the operator asked; this repo is frontend-first for product work.
- Do not commit `docker/mosquitto/config/pwfile`, Mosquitto data/log, or `local_server/data/`.

## 7. Optional recognition stack

Only if the operator asked for plate recognition or live cameras.

HyperLPR3 (first **image build can exceed 10 minutes**):

Windows: `powershell -File ai-build/windows/up.ps1 -WithLpr`

Linux / macOS: `sh ai-build/linux/up.sh --with-lpr`

Listen port `8715`. Then in the running console: System settings → enable HyperLPR3 → base URL `http://127.0.0.1:8715`.

Frigate (needs working RTSP in `docker/frigate/config/config.yml`):

Windows: `powershell -File ai-build/windows/up.ps1 -WithFrigate`

Linux / macOS: `sh ai-build/linux/up.sh --with-frigate`

Ports: Frigate UI `5000`, go2rtc `1984`. In the console: Frigate docking → API `127.0.0.1:5000`, MQTT `127.0.0.1:1883`, topic prefix `frigate`. Camera ids must match go2rtc stream names (e.g. `cam_acfb0350`), not the Chinese friendly name.

## Ports (do not remap unless asked)

| Port | Service |
| --- | --- |
| 3307 | MySQL 8.4 |
| 1883 | Mosquitto MQTT |
| 8081 | local_server |
| 5173 | local_frontend (Vite; +1 if busy) |
| 8715 | HyperLPR3 (optional) |
| 5000 / 1984 | Frigate / go2rtc (optional) |

## Facts other AIs get wrong

- There is **no** reactor parent POM. Always `install` `driver-api` then `zhensi-driver` before `local_server`.
- Use `local_server/mvnw` / `mvnw.cmd` so a global Maven install is not required.
- Frontend README says `npm install` (there is also a `pnpm-lock.yaml`; still use npm unless the operator says otherwise).
- Cloud MQTT docs (`local_server/docs/mqtt-integration.md`) are for EDGE↔cloud. Local Frigate MQTT is the Mosquitto container on `1883`.
