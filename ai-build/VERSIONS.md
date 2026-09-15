# Pinned versions / 钉死版本

Local deploy is **only** these five services. Do not add MQTT (or any other container) on site.

场端本地只部署下面五个服务。不要在场端再起 MQTT 或其它容器。

| Service | How it runs | Pinned version | Notes |
| --- | --- | --- | --- |
| `local_server` | JDK 21 process (`mvnw`) | Spring Boot **4.1.1**, Java **21** | Port `8081` |
| `local_frontend` | Node process (`npm run dev`) | Vue **3** + Vite, Node **22+** | Port `5173` |
| MySQL | Docker | **`mysql:8.4.11`** | Host port `3307`. 8.4 LTS; do not use `mysql:8` / `8.0` / `9` / `latest`. |
| Frigate | Docker | **`ghcr.io/blakeblackshear/frigate:0.17.2`** | Config schema `version: 0.17-0`. Do **not** use `:stable` (it moves). |
| HyperLPR3 | Docker build from `docker/hyperlpr3` | PyPI **`hyperlpr3==0.1.3`** on **`python:3.10.21-slim-bookworm`** | Image tag `freepark-hyperlpr3:0.1.3`. Port `8715`. |

HyperLPR3 runtime pins (from the working image, 2026-09-15):

| Package | Version |
| --- | --- |
| `hyperlpr3` | 0.1.3 |
| `numpy` | 1.26.4 |
| `opencv-python-headless` | 4.11.0.86 |
| `onnxruntime` | 1.23.2 |
| `fastapi` | 0.141.1 |
| `uvicorn` | 0.52.4 |
| `Pillow` | 12.3.0 |
| `python-multipart` | 0.0.32 |
| `loguru` | 0.7.3 |

Do not bump these tags without a compatibility check (Frigate config schema, JDBC, HyperLPR3 REST).

升级上述标签前必须做兼容验证（Frigate 配置 schema、JDBC、HyperLPR3 REST）。
