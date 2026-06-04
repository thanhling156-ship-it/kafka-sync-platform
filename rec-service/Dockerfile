# Stage 1: Build library và service
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# CHÚ Ý: Copy file pom.xml cha ở thư mục gốc vào trước
COPY pom.xml /app/pom.xml

# 1. Build thư viện dùng chung
COPY event-library /app/event-library
RUN mvn -f /app/event-library/pom.xml clean install -DskipTests

# 2. Build service
COPY rec-service /app/rec-service
RUN mvn -f /app/rec-service/pom.xml clean package -DskipTests

# Stage 2: Cấu hình chạy JRE tối ưu
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/rec-service/target/*.jar app.jar

ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]