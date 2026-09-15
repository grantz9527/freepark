# Mosquitto 部署

MQTT Broker 统一放在 `docker/mosquitto/`。云端与场端（含 Frigate）联调都连这个 Broker，端口 `1883`。

## 目录

| 路径 | 说明 |
| --- | --- |
| `docker/mosquitto/docker-compose.yml` | 容器编排 |
| `docker/mosquitto/config/mosquitto.conf` | Broker 配置 |
| `docker/mosquitto/config/pwfile` | 密码文件（不入库，需本机生成或沿用旧文件） |
| `docker/mosquitto/data/`、`docker/mosquitto/log/` | 持久化与日志（不入库） |

## 启动

在仓库根目录：

```sh
docker compose -f docker/mosquitto/docker-compose.yml up -d
```

查看日志：

```sh
docker compose -f docker/mosquitto/docker-compose.yml logs -f mosquitto
```

## 账号

默认用户 `freepark` / `freepark`（`allow_anonymous false`）。

若没有 `config/pwfile`，在仓库根目录生成：

```sh
docker run --rm -v "${PWD}/docker/mosquitto/config:/mosquitto/config" eclipse-mosquitto:2 \
  mosquitto_passwd -c -b /mosquitto/config/pwfile freepark freepark
```

## 端口

| 端口 | 用途 |
| --- | --- |
| `1883` | MQTT。场端 / 云端 / Frigate 填 `127.0.0.1:1883` |
| `9001` | 已映射，当前 conf **未开 websocket**，不要依赖 |

容器内访问宿主机 MQTT 使用 `host.docker.internal:1883`（例如 Frigate）。

一键部署见 [`ai-build`](../../ai-build/README.md)。
