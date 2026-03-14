# Stage 1: Build library và service
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Build thư viện dùng chung trước
COPY event-library /app/event-library
RUN mvn -f /app/event-library/pom.xml clean install -DskipTests

# 2. Build service (ví dụ order-service)
COPY order-service /app/order-service
RUN mvn -f /app/order-service/pom.xml clean package -DskipTests

# Stage 2: Chạy app (Sử dụng JRE nhẹ cho Docker image nhỏ)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/order-service/target/*.jar app.jar

# Tối ưu cho Microservices: Thêm thông số RAM/CPU cho Java
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]