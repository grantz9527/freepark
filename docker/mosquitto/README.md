# Mosquitto（调试）

本仓库场端默认**不**部署 MQTT。这套 compose 只给本机调试用，账号 `freepark` / `freepark`，端口 `1883`。

## 启动

仓库根目录：

```sh
docker compose -f docker/mosquitto/docker-compose.yml up -d
```

若没有 `config/pwfile`：

```sh
docker run --rm -v "${PWD}/docker/mosquitto/config:/mosquitto/config" eclipse-mosquitto:2 \
  mosquitto_passwd -c -b /mosquitto/config/pwfile freepark freepark
```

Windows PowerShell：

```powershell
docker run --rm -v "${PWD}/docker/mosquitto/config:/mosquitto/config" eclipse-mosquitto:2 mosquitto_passwd -c -b /mosquitto/config/pwfile freepark freepark
```

## 连接

| 从哪连 | Host | 端口 | 账号 |
| --- | --- | --- | --- |
| 本机控制台 / 节点配置 | `127.0.0.1` | 1883 | `freepark` / `freepark` |
| Frigate 容器内 | `host.docker.internal` | 1883 | 同上 |

`pwfile`、`data/`、`log/` 不入库。
