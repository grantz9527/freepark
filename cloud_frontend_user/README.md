# cloud_frontend_user

Vue 3 user app for the FreePark cloud platform. Uses `vue-i18n` and talks to [`cloud_server`](../cloud_server/README.md).

FreePark 云端用户界面，基于 Vue 3 + vue-i18n，对接 `cloud_server`。

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

Dev server: [http://localhost:5175](http://localhost:5175)

Vite proxies `/api` and `/actuator` to `cloud_server` at `http://localhost:8080`. Start the backend first:

```sh
cd ../cloud_server
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
