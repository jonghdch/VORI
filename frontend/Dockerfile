FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY public public
COPY src src
ARG REACT_APP_GOOGLE_CLIENT_ID
ARG REACT_APP_AI_ACTIVE_FROM_HOUR
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/build /usr/share/nginx/html
