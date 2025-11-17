# 🎁 GiftBot --- Telegram-бот для управления заказами

[![Java
Version](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/)
[![Spring
Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-brightgreen)](https://spring.io/projects/spring-boot)
[![Telegram Bot
API](https://img.shields.io/badge/Telegram%20Bot%20API-6.9-blue)](https://core.telegram.org/bots/api)
[![Database](https://img.shields.io/badge/SQLite-lightgrey)](https://www.sqlite.org/)

**GiftBot** --- это современный Telegram-бот, созданный на Spring Boot и
предназначенный для автоматизации обработки заказов товаров из Китая.

## 🚀 Возможности

### 🛍️ Управление заказами

-   Полный цикл обработки заказов
-   Поддержка ссылок, скриншотов и фотографий
-   Несколько изображений для одного заказа
-   Отслеживание статуса
-   Автоматическая нумерация

### 👤 Управление пользователями

-   Регистрация и аутентификация
-   Сбор username, ФИО, телефона и адреса
-   История заказов

### 📋 Администрирование

-   Подтверждение заказов
-   Экспорт в Excel
-   Управление сайтами
-   Очистка базы

## 🛠️ Технологии

-   **Java 17+**
-   **Spring Boot 3.2+**
-   **SQLite**
-   **Maven**
-   **Telegram Bot API**

## 📁 Структура проекта

    GiftBot/
    ├── src/main/java/com/example/
    │   ├── bot/GiftBot.java
    │   ├── config/BotConfig.java
    │   ├── model/Order.java
    │   └── GiftBotApplication.java
    ├── src/main/resources/application.properties
    ├── orders.db
    ├── Dockerfile
    ├── compose.yaml
    ├── pom.xml
    └── Procfile

## 🚀 Быстрый старт

``` bash
git clone <url>
cd GiftBot
```

### Конфигурация

``` properties
telegram.bot.token=ВАШ_ТОКЕН
telegram.bot.username=ВАШ_ЮЗЕРНЕЙМ
admin.id=ВАШ_ID
spring.datasource.url=jdbc:sqlite:orders.db
spring.datasource.driver-class-name=org.sqlite.JDBC
```

### Сборка и запуск

``` bash
./mvnw clean package
java -jar target/giftbot-1.0.0.jar
```

## 🤖 Команды

### Пользователь

-   /start
-   /order
-   /sites
-   /status
-   /admin

### Админ

-   /orders
-   /approve `<id>`{=html}
-   /addsite `<описание>`{=html} `<url>`{=html}
-   /clearorders

## 💾 База данных

``` sql
CREATE TABLE orders (
    order_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    username TEXT,
    order_details TEXT,
    last_name TEXT,
    first_name TEXT,
    patronymic TEXT,
    phone TEXT,
    address TEXT,
    status TEXT
);
```

## 🧪 Тесты

``` bash
./mvnw test
```

## 📝 Лицензия

Проект предназначен для образовательного и коммерческого использования.
