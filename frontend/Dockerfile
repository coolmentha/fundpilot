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

# 拷构建产物
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
