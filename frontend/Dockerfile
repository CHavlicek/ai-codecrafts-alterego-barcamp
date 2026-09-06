# Stage 1 — install + build with Node
FROM node:20-alpine AS build
WORKDIR /workspace
# Install deps with deterministic layer caching
COPY package.json package-lock.json* ./
RUN npm ci
# Copy sources and produce the production bundle
COPY tsconfig.json vite.config.ts index.html ./
COPY src ./src
RUN npm run build

# Stage 2 — serve the static bundle with Nginx and proxy /api → backend
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
