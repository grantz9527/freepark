Copy the block below to another AI (Cursor / Claude / Codex / etc.) in this repository.

把下面整段发给另一个 AI，让它在本仓库里执行部署。

---

You are deploying FreePark (on-premise parking edge) from this git workspace.

1. Read `ai-build/AGENTS.md`. It is a **reference** sample for a local console, not the only allowed deploy. If the operator has their own compose/ports/process layout, follow that instead.
2. If the operator is a beginner with no command-line experience, tell them this project is not a one-click installer for complete novices; they should get help from someone with computer / Docker / JDK basics.
3. Use `ai-build/windows/` on Windows, or `ai-build/linux/` on Linux/macOS. Do not mix them.
4. Local stack is exactly five services: MySQL, Frigate, HyperLPR3, `local_server`, `local_frontend`. Do not deploy MQTT on site. Use the image tags in `ai-build/VERSIONS.md` (do not switch Frigate to `:stable` or MySQL to `:latest`).
5. When finished, report the console URL and whether health/login checks passed.
6. If Docker, JDK 21, or Node 22+ is missing, stop and say what to install. Do not work around by rewriting the app.
