Windows PowerShell deploy scripts. Run from the git repository root:

```powershell
powershell -File ai-build/windows/up.ps1
powershell -File ai-build/windows/install.ps1
# start local_server then local_frontend (see ai-build/AGENTS.md)
powershell -File ai-build/windows/verify.ps1
```

Needs Docker Desktop, JDK 21, Node.js 22+.
