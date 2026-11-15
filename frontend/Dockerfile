FROM node:20-bookworm AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# ATTENTION(security): non-root UID 10001 (MISSION_v2 §6). Port 3000 > 1024 so no
# root binding needed; cache/pid dirs are owned by appuser (fix config rather than
# falling back to another UID).
RUN adduser -u 10001 -D appuser \
 && mkdir -p /var/cache/nginx /var/run /tmp/nginx \
 && chown -R appuser /var/cache/nginx /var/run /tmp/nginx
USER 10001
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-main.conf /etc/nginx/nginx.conf
EXPOSE 3000
