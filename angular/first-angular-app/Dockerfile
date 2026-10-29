# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /workspace
COPY package*.json ./

# Keep the npm download cache between builds and tolerate slow/unstable networks.
# Override at build time when a proxy or internal registry is required:
#   docker compose build --build-arg NPM_REGISTRY=https://<registry>/ web
ARG NPM_REGISTRY=https://registry.npmjs.org/
RUN --mount=type=cache,target=/root/.npm \
    npm ci \
      --registry="${NPM_REGISTRY}" \
      --fetch-retries=5 \
      --fetch-retry-factor=2 \
      --fetch-retry-mintimeout=20000 \
      --fetch-retry-maxtimeout=120000 \
      --fetch-timeout=300000 \
      --no-audit \
      --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/first-angular-app/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD wget -q -O - http://127.0.0.1/healthz || exit 1
