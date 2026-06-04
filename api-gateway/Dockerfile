# Stage 1: Build mã nguồn
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Khởi chạy ứng dụng
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Khai báo rõ vị trí copy từ Stage 1 sang thư mục làm việc hiện tại
COPY --from=build /app/target/*.jar app.jar

# SỬA TẠI ĐÂY: Xóa dấu gạch chéo '/' ở trước app.jar để Java tìm đúng file trong thư mục /app
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]