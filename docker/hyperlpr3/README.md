# HyperLPR3

社区版 REST，端口 `8715`。钉死 **PyPI `hyperlpr3==0.1.3`**，基础镜像 **`python:3.10.21-slim-bookworm`**，产物镜像 **`freepark-hyperlpr3:0.1.3`**。版本总表：[`ai-build/VERSIONS.md`](../../ai-build/VERSIONS.md)。

首次 `docker compose build` 会拉依赖和模型，可能超过 10 分钟。

```sh
docker compose -f docker/hyperlpr3/docker-compose.yml up -d --build
```

健康检查：http://127.0.0.1:8715/api/v1/docs

场端系统设置里启用 HyperLPR3，服务地址填 `http://127.0.0.1:8715`。

一键部署请用仓库根目录的 [`ai-build`](../../ai-build/README.md)（会一并拉起 MySQL、Frigate、HyperLPR3）。
