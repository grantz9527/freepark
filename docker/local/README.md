# FreePark 场端镜像

控制台（Vue）和 `local_server`（含臻识驱动）打成 **一个** 镜像 `freepark-local:0.0.1`。MySQL `8.4.11`、Frigate `0.17.2`、HyperLPR3 `0.1.3` 仍用各自官方/本仓库镜像（Frigate 体积大、要独立 shm/摄像头，不宜塞进同一 Dockerfile）。

## 构建本项目镜像

仓库根目录：

```powershell
powershell -File ai-build/windows/build-image.ps1
```

```sh
sh ai-build/linux/build-image.sh
```

产物：`freepark-local:0.0.1`（控制台走 **8081**，不再需要本机 Node / JDK）。

## 连同依赖一起启动

```powershell
docker compose -f docker/local/docker-compose.yml up -d --build
```

打开 http://127.0.0.1:8081 ，默认 `admin` / `admin123`。

控制台跑在容器里时，不要把 Frigate / MQTT / HyperLPR3 填成 `127.0.0.1`（那是容器自己）。compose 会把 Frigate API 和 MQTT 默认成 `host.docker.internal`（走宿主机已发布端口）。同 compose 网络下也可以填服务名 `frigate`。HyperLPR3 填 `http://host.docker.internal:8715`。

## 导出离线包（四个镜像一个 tar）

```powershell
powershell -File ai-build/windows/save-images.ps1
```

得到 `ai-build/dist/freepark-images.tar`。目标机器：

```sh
docker load -i freepark-images.tar
docker compose -f docker/local/docker-compose.yml up -d
```

不要把 tar 提交进 git。
