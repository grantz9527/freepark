Copy the block below to another AI (Cursor / Claude / Codex / etc.) in this repository.

把下面整段发给另一个 AI，让它在本仓库里执行部署。

---

You are deploying FreePark (on-premise parking edge) from this git workspace.

1. Read and follow `ai-build/AGENTS.md` exactly. That file is the deploy contract.
2. Do not invent ports, compose files, or default passwords.
3. Use `ai-build/windows/` on Windows, or `ai-build/linux/` on Linux/macOS. Do not mix them.
4. Do not start Frigate or HyperLPR3 unless the operator asked, or `ai-build/AGENTS.md` optional sections apply.
5. When finished, report the console URL and whether health/login checks passed.
6. If Docker, JDK 21, or Node 22+ is missing, stop and say what to install. Do not work around by rewriting the app.
