# AI one-click deploy

This folder is the **only** deploy contract for FreePark. Another AI (or a human) should start here, not by guessing from scattered READMEs.

本目录是 FreePark **唯一**的部署约定。其它 AI 或人类应从这里开始，不要从各子项目 README 自行拼命令。

Pick **one** OS directory and ignore the other:

| Path | OS |
| --- | --- |
| [windows/](windows/README.md) | Windows (PowerShell) |
| [linux/](linux/README.md) | Linux **and** macOS (`sh`) |

Shared (not OS-specific):

| File | Purpose |
| --- | --- |
| [AGENTS.md](AGENTS.md) | Step-by-step procedure the agent must execute |
| [PROMPT.md](PROMPT.md) | Copy-paste prompt to hand to another AI |
| [docker-compose.yml](docker-compose.yml) | MySQL + Mosquitto; optional HyperLPR3 / Frigate |

Default console after a successful deploy: **http://localhost:5173** — `admin` / `admin123`.
