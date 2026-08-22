# local_frontend

Vue 3 frontend for the FreePark on-site console. Uses `vue-i18n` and talks to [`local_server`](../local_server/README.md).

FreePark 场端控制台前端，基于 Vue 3 + vue-i18n，对接 `local_server`。

## Stack

- Vue 3 + Vite + TypeScript
- Vue Router
- vue-i18n (Composition API)
- Locales: `en`, `zh-CN`, `zh-TW`, `ja`, `ko`, `es`, `fr`, `de`, `pt`, `ar`

## Setup

```sh
npm install
npm run dev
```

Dev server: [http://localhost:5173](http://localhost:5173)

Vite proxies `/api` and `/actuator` to `local_server` at `http://localhost:8081`. Start the backend first:

```sh
cd ../local_server
mvnw.cmd spring-boot:run
```

## I18N

- UI strings live in `src/i18n/messages/*.json`
- The language switcher stores the choice in `localStorage`
- API calls send `Accept-Language` and `?lang=`
- Arabic (`ar`) switches the page to `dir="rtl"`

## Scripts

```sh
npm run dev
npm run build
npm run lint
```
