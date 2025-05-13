# Этап сборки
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
# Копируем Maven Wrapper и конфигурацию
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Устанавливаем права на выполнение
RUN chmod +x ./mvnw
# Кэшируем зависимости
RUN ./mvnw dependency:go-offline -B
# Копируем исходный код
COPY src ./src
# Собираем проект
RUN ./mvnw clean package -DskipTests
# Финальный образ
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/GiftBot-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]