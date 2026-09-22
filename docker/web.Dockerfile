# 前端多阶段构建：Vite 打包 -> nginx 托管
FROM node:22-alpine AS build
WORKDIR /src
RUN npm config set registry https://registry.npmmirror.com
COPY web/package.json ./package.json
RUN npm install
COPY web/ ./
RUN npm run build

FROM nginx:latest
COPY --from=build /src/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
