FROM node:24-alpine

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY vite.config.js ./
COPY index.html ./
COPY src ./src
COPY public ./public

EXPOSE 5157

CMD ["npm", "run", "dev"]
