# AI deploy (reference)

Sample scripts and steps so another AI (or a human) can stand up a **dev-style** local console. **This is a reference, not a requirement.** Deploy however you need: different compose files, systemd, a single JAR, another reverse proxy, other ports.

本目录是参考部署，不是强制方案。可按自己的需求改 Docker、端口、进程与发布方式。

If you are new to Docker / JDK / the command line, ask someone with computer experience to deploy this project.

若你是电脑小白，建议找有一定电脑基础的人员来部署本项目。

Pick **one** OS directory if you use these scripts:

| Path | OS |
| --- | --- |
| [windows/](windows/README.md) | Windows (PowerShell) |
| [linux/](linux/README.md) | Linux **and** macOS (`sh`) |

Shared (not OS-specific):

| File | Purpose |
| --- | --- |
| [AGENTS.md](AGENTS.md) | Sample step-by-step procedure (reference) |
| [PROMPT.md](PROMPT.md) | Copy-paste prompt to hand to another AI |
| [docker-compose.yml](docker-compose.yml) | MySQL 8.4.11 + HyperLPR3 0.1.3 + Frigate 0.17.2. No MQTT. |
| [VERSIONS.md](VERSIONS.md) | Pinned image / package versions |

Default console after a successful deploy: **http://localhost:5173** — `admin` / `admin123`.
