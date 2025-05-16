FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Копируем Maven Wrapper и конфигурацию
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Устанавливаем права на выполнение для mvnw
RUN chmod +x mvnw

# Кэшируем зависимости
RUN ./mvnw dependency:go-offline -B

# Копируем исходный код
COPY src ./src

# Собираем приложение
RUN ./mvnw clean package -DskipTests

# Второй этап: финальный образ
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]