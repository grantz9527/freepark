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

## License / 许可证

License is not decided yet.

许可证待定。
