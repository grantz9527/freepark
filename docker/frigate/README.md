# Frigate 部署

识别与直播容器统一放在 `docker/frigate/`。MQTT 用 `docker/mosquitto/` 的 Mosquitto（`1883`）。

## 目录

| 路径 | 说明 |
| --- | --- |
| `docker/frigate/docker-compose.yml` | 容器编排 |
| `docker/frigate/config/config.yml` | 相机、LPR、go2rtc、MQTT |
| `docker/frigate/config/backup_config.yaml` | 空配置备份 |

## 启动

先起 MQTT（若尚未运行）：

```sh
docker compose -f docker/mosquitto/docker-compose.yml up -d
```

确保已有存储卷（只需一次）：

```sh
docker volume create frigate-storage
```

再起 Frigate（在仓库根目录）：

```sh
docker compose -f docker/frigate/docker-compose.yml up -d
```

查看日志：

```sh
docker compose -f docker/frigate/docker-compose.yml logs -f frigate
```

## 端口

| 端口 | 用途 |
| --- | --- |
| `5000` | Frigate Web / HTTP API。场端「Frigate 对接」填 `127.0.0.1:5000` |
| `1984` | go2rtc。一体机预览兜底 |
| `8554` | go2rtc RTSP |
| `8555` | WebRTC |
| `1935` | RTMP |

容器访问宿主机 MQTT 使用 `host.docker.internal:1883`（账号 `freepark` / `freepark`），见 `config/config.yml`。

## 场端对接

1. 打开 Frigate UI：http://localhost:5000
2. 本机控制台 → **Frigate 对接**：API `127.0.0.1` 端口 `5000`，MQTT `127.0.0.1` 端口 `1883`，topic 前缀 `frigate`
3. 相机名与 `config.yml` 里的 id 一致（如 `cam_acfb0350`），不要只用中文友好名
4. `local_server` 预览走 `5000/api/go2rtc`，失败再试 `1984`

一键部署（可选 Frigate）见 [`ai-build`](../../ai-build/README.md)。


改相机 RTSP：编辑 `config.yml` 后执行：

```sh
docker compose -f docker/frigate/docker-compose.yml restart
```
