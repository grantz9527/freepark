FROM python:3.10-slim-bookworm

ENV DEBIAN_FRONTEND=noninteractive \
    PIP_NO_CACHE_DIR=1 \
    PYTHONUNBUFFERED=1

# 安装编译/OpenCV系统依赖（glib/sm/xrender/xext 等是 opencv-python-headless 在 slim 下可能用到的）
# 用清华 TUNA Debian 镜像避免 deb.debian.org CDN 偶发 500
RUN sed -i 's|http://deb.debian.org/debian|https://mirrors.tuna.tsinghua.edu.cn/debian|g; s|http://security.debian.org/debian-security|https://mirrors.tuna.tsinghua.edu.cn/debian-security|g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || true; \
    if [ -f /etc/apt/sources.list ]; then \
      sed -i 's|http://deb.debian.org/debian|https://mirrors.tuna.tsinghua.edu.cn/debian|g; s|http://security.debian.org/debian-security|https://mirrors.tuna.tsinghua.edu.cn/debian-security|g' /etc/apt/sources.list; \
    fi; \
    apt-get update -o Acquire::Retries=5 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        --fix-missing \
        -o Acquire::Retries=5 \
        -o Acquire::http::Timeout=30 \
        libglib2.0-0 libsm6 libxrender1 libxext6 libgl1 libgomp1 \
        ca-certificates curl git build-essential cmake python3-dev && \
    rm -rf /var/lib/apt/lists/*

# 安装 hyperlpr3 + 推理引擎 + 轻量 OpenCV
# 顺序：先装 opencv-python-headless + onnxruntime，再 --no-deps 装 hyperlpr3，
# 避免 hyperlpr3 的声明依赖把 opencv-python（非 headless）带进来，导致 slim 镜像缺图形库。
# 加 TUNA pypi 镜像提升速度与稳定性。
RUN pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple ; \
    pip config set global.trusted-host pypi.tuna.tsinghua.edu.cn ; \
    pip install --upgrade pip setuptools wheel && \
    pip install --no-cache-dir --default-timeout=180 \
        "numpy>=1.22,<2" \
        "opencv-python-headless>=4.8,<5" \
        "onnxruntime>=1.16,<2" \
        "Pillow>=9" "requests" "tqdm" "loguru" \
        "fastapi>=0.110,<1" "uvicorn>=0.27,<1" "python-multipart>=0.0.9,<1" && \
    pip install --no-cache-dir --default-timeout=180 --no-deps hyperlpr3

# 预热：触发模型文件下载（部分版本首次运行会拉取权重，避免容器第一次请求超时）
RUN lpr3 --help || true

EXPOSE 8715
HEALTHCHECK --interval=30s --timeout=6s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8715/api/v1/docs || exit 1

# workers=1 避免多进程重复加载大模型占内存
CMD ["lpr3", "rest", "--port", "8715", "--host", "0.0.0.0", "--workers", "1"]
