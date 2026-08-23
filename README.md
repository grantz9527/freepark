# FreePark

Use AI and related open-source tools to build an I18N (internationalized) parking system. This system is intended to help people around the world solve parking problems.

用 AI 与开源工具构建一套国际化（I18N）停车系统，帮助全球用户更高效地解决停车问题。

## Vision / 愿景

- Make it easier to find, share, and manage parking spaces across countries and cities.
- 降低找车位、共享车位、管理停车资源的成本，覆盖多国家、多城市场景。

## Goals / 目标

- **I18N first**: language, locale, currency, time zone, and map data should work worldwide.
- **AI-assisted**: use AI to improve search, matching, occupancy prediction, and operations.
- **Open source**: prefer existing open-source components over reinventing the stack.
- **国际化优先**：语言、地区、货币、时区、地图数据面向全球可用。
- **AI 辅助**：用 AI 提升搜索、匹配、占用预测与运营效率。
- **开源优先**：尽量复用成熟开源组件，而不是从零造轮子。

## Backend / 后端

Both backends use Java 21, Spring Data JPA, MySQL, and HTTP I18N (`Accept-Language` or `?lang=`).

两套后端都使用 Java 21、Spring Data JPA、MySQL，并支持接口国际化。

- [`cloud_server`](cloud_server/README.md): cloud service, default port `8080`
- [`cloud_frontend_mnt`](cloud_frontend_mnt/README.md): Vue 3 + vue-i18n cloud admin console, default port `5174`
- [`cloud_frontend_user`](cloud_frontend_user/README.md): Vue 3 + vue-i18n cloud user app, default port `5175`
- [`local_server`](local_server/README.md): on-premise / site service, default port `8081`
- [`local_frontend`](local_frontend/README.md): Vue 3 + vue-i18n console for `local_server`, default port `5173`

## Status / 当前状态

Backend scaffolding is in place. Product design and remaining services are in progress.

后端骨架已就绪。产品设计与其余服务仍在进行中。

## Contributing / 参与

Issues and pull requests are welcome.

欢迎提交 Issue 和 Pull Request。

## Ethical use / 伦理使用

FreePark is for people and organizations that **respect workers' rights**. We oppose exploitative labor practices.

FreePark 面向**尊重劳动者权益**的个人与组织。我们反对下列剥削性做法。

**Organizations that practice any of the following may not use this software** (including deployment, operations, or offering it as a service):

**实施以下任一做法的组织不得使用本软件**（含部署、运营或对外提供服务）：

- **“996”** and systemic exploitative overtime / **996** 与系统性剥削性加班
- **Unequal pay for equal work** / **同工不同酬**
- **Labor outsourcing** used to evade employer duties and worker protections / 以规避用工责任、损害劳动者保障的**人力外包**

Full policy: [ETHICAL_USE.md](ETHICAL_USE.md)

完整声明见 [ETHICAL_USE.md](ETHICAL_USE.md)。

## License / 许可证

Copyright (C) 2026 顾文斌

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

- Full license text: [LICENSE](LICENSE)
- Summary: you may use, modify, and distribute this software; if you run a modified version as a network service, you must offer corresponding source to users. See the license for full terms.
- Ethical expectations: [ETHICAL_USE.md](ETHICAL_USE.md) (read together with AGPL).

本项目采用 **GNU Affero 通用公共许可证 v3.0（AGPL-3.0）** 授权。

- 完整协议文本见 [LICENSE](LICENSE)
- 简要说明：可自由使用、修改和分发；若将修改后的版本作为网络服务提供，须向用户提供相应源代码。具体权利与义务以协议全文为准。
- 伦理使用期望见 [ETHICAL_USE.md](ETHICAL_USE.md)（与 AGPL 一并阅读）。
