FROM node:22-alpine AS build

WORKDIR /app

COPY pnpm-lock.yaml pnpm-workspace.yaml ./
COPY frontend/package.json frontend/package.json

RUN corepack enable \
    && corepack prepare pnpm@11.7.0 --activate \
    && pnpm --dir frontend install --frozen-lockfile

COPY frontend frontend
RUN pnpm --dir frontend build

FROM nginx:1.27-alpine

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html

EXPOSE 80
