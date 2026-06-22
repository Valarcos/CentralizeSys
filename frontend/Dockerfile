# Stage 1: Build the React application
FROM node:20-alpine AS build
WORKDIR /app

# Copy package files and install dependencies
COPY package*.json ./
RUN npm ci

# Copy the rest of the application
COPY . .

# Build the application for production
# This will automatically pick up variables from .env.production
RUN npm run build

# Stage 2: Serve the application with Nginx
FROM nginx:alpine

# Copy the custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the built assets from Stage 1
COPY --from=build /app/dist /usr/share/nginx/html

# Expose port 80 (internal to Docker network)
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
