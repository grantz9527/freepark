# Deploy FreePark (agent procedure, reference)

These steps are a **sample** path for a local console. Operators may deploy differently (other compose, ports, or process managers). Do not treat this file as the only allowed topology.

若操作者另有部署方式，按其要求执行，不必套用本文件的每一条。

If the operator is a beginner with no command-line experience, stop and recommend they get help from someone comfortable with Docker, JDK, and Node.js.

若操作者是电脑小白且无人协助，先说明本项目不适合零基础独自部署，建议找有一定电脑基础的人员。

Execute the following only when using this sample path. Stop if a required tool is missing. Do not skip health checks.

**Pick one OS tree and stay in it:**

- Windows → `ai-build/windows/` (`*.ps1` only)
- Linux or macOS → `ai-build/linux/` (`*.sh` only)

Do not mix the two trees.

Required on the host:

- Docker Engine + `docker compose` (Windows/Mac: Docker Desktop)
- JDK 21 (for `local_server/mvnw`)
- Node.js `^22.18.0 || >=24.12.0` and `npm`

**Local stack is exactly five services** (see [`VERSIONS.md`](VERSIONS.md)). Do **not** deploy an MQTT broker on the site.

| Service | Version | Port |
| --- | --- | --- |
| MySQL | `mysql:8.4.11` | 3307 |
| Frigate | `ghcr.io/blakeblackshear/frigate:0.17.2` | 5000 / 1984 |
| HyperLPR3 | `hyperlpr3==0.1.3` (`python:3.10.21-slim-bookworm`) | 8715 |
| `local_server` | Spring Boot 4.1.1, Java 21 | 8081 |
| `local_frontend` | Vue 3 + Vite, Node 22+ | 5173 |

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

- `127.0.0.1:3307` — MySQL 8.4.11 (`freepark-local-mysql`)
- `http://127.0.0.1:8715/api/v1/docs` — HyperLPR3 0.1.3
- `http://127.0.0.1:5000` — Frigate 0.17.2 (go2rtc `1984`)

First HyperLPR3 image build can exceed 10 minutes. Do not retag MySQL / Frigate / HyperLPR3; pins live in [`VERSIONS.md`](VERSIONS.md).

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

- Do not deploy Mosquitto or any MQTT broker on the site.
- Do not pull `frigate:stable` or `mysql:latest` — use the pins in [`VERSIONS.md`](VERSIONS.md).
- Do not put video on MQTT. MQTT is control/state only (cloud broker).
- Do not change i18n locales beyond zh-CN / zh-TW / en when editing product copy.
- Do not implement backend features unless the operator asked; this repo is frontend-first for product work.
- Do not commit `local_server/data/`.

## 7. After Docker is up

In the running console:

- System settings → enable HyperLPR3 → base URL `http://127.0.0.1:8715`
- Frigate docking → API `127.0.0.1:5000`, MQTT = **external/cloud broker** (not this repo), topic prefix `frigate`
- Camera ids must match go2rtc stream names in `docker/frigate/config/config.yml` (e.g. `cam_acfb0350`), not the Chinese friendly name

Frigate needs working RTSP in that config. If cameras are unreachable, the container may still run; the console and MySQL still work.

## Ports (do not remap unless asked)

| Port | Service |
| --- | --- |
| 3307 | MySQL 8.4.11 |
| 8081 | local_server |
| 5173 | local_frontend (Vite; +1 if busy) |
| 8715 | HyperLPR3 0.1.3 |
| 5000 / 1984 | Frigate 0.17.2 / go2rtc |

## Facts other AIs get wrong

- There is **no** reactor parent POM. Always `install` `driver-api` then `zhensi-driver` before `local_server`.
- Use `local_server/mvnw` / `mvnw.cmd` so a global Maven install is not required.
- Frontend README says `npm install` (there is also a `pnpm-lock.yaml`; still use npm unless the operator says otherwise).
- Cloud MQTT docs (`local_server/docs/mqtt-integration.md`) are EDGE↔**cloud broker**. This repo does **not** run Mosquitto on the site.
