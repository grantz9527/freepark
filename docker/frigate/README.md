# Frigate 部署

识别与直播容器统一放在 `docker/frigate/`。镜像钉死为 **`ghcr.io/blakeblackshear/frigate:0.17.2`**（配置 `version: 0.17-0`）。不要用会滚动的 `:stable`。**场端不部署 MQTT Broker**；Frigate 与场端控制台里的 MQTT 地址请填云端或其它已有 Broker。版本总表：[`ai-build/VERSIONS.md`](../../ai-build/VERSIONS.md)。

## 目录

| 路径 | 说明 |
| --- | --- |
| `docker/frigate/docker-compose.yml` | 容器编排 |
| `docker/frigate/config/config.yml` | 相机、LPR、go2rtc、外部 MQTT |
| `docker/frigate/config/backup_config.yaml` | 空配置备份 |

## 启动

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

`config.yml` 的 `mqtt.host` 指向**外部 Broker**，不要在本仓库起 Mosquitto。

## 场端对接

1. 打开 Frigate UI：http://localhost:5000
2. 本机控制台 → **Frigate 对接**：API `127.0.0.1` 端口 `5000`，MQTT 填云端/外部 Broker，topic 前缀 `frigate`
3. 相机名与 `config.yml` 里的 id 一致（如 `cam_acfb0350`），不要只用中文友好名
4. `local_server` 预览走 `5000/api/go2rtc`，失败再试 `1984`

一键部署（可选 Frigate）见 [`ai-build`](../../ai-build/README.md)。

改相机 RTSP：编辑 `config.yml` 后执行：

```sh
docker compose -f docker/frigate/docker-compose.yml restart
```
