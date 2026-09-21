# syntax=docker/dockerfile:1

# ---- 构建阶段:Vite 打包 ----
FROM node:22-alpine AS build
WORKDIR /app

# 先拷依赖清单,单独成层;后续代码变动不重装 node_modules
COPY package.json package-lock.json ./
RUN npm ci

# 拷源码,构建产物到 dist/
COPY . .
RUN npm run build

# ---- 运行阶段:Nginx 托管静态资源 ----
FROM nginx:alpine

# 拷 SPA 路由配置(含 /api 反代到后端)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 上面已覆盖 default.conf,官方 10-listen-on-ipv6-by-default.sh 必然判定"已被修改"后退出,
# 但它在退出前会执行 `apk manifest nginx` 去 dl-cdn.alpinelinux.org 取包清单做校验;
# 该请求在 VPS 上实测耗时约 100 秒,期间 nginx 尚未监听,直接删除省掉这段空等。
RUN rm /docker-entrypoint.d/10-listen-on-ipv6-by-default.sh

# 拷构建产物
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
