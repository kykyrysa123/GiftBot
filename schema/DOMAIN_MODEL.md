# GiftBot - Доменная модель (Диаграмма классов)

## Обзор
Этот документ описывает ключевые сущности и их взаимосвязи в домене приложения **GiftBot**, основанные на схеме базы данных и проектировании системы. Данная модель служит концептуальной основой для объектно-ориентированной реализации и структуры базы данных.

## Схема диаграммы классов

![Диаграмма классов](/docs/schema/uml_diagramma_class.png)

## Описание диаграммы классов
Основной домен **GiftBot** вращается вокруг заказов, пользователей, товаров и административных функций. Основные сущности:

* **`User`**: Представляет пользователя системы с учетными данными Telegram
* **`Order`**: Представляет заказ подарка с деталями товара и информацией о клиенте
* **`Product`**: Представляет информацию о товарах с различных сайтов
* **`Site`**: Представляет поддерживаемые интернет-магазины для поиска товаров
* **`Photo`**: Представляет фотографии товаров, загруженные пользователями
* **`Admin`**: Представляет администраторов системы с правами управления
* **`OrderStatus`**: Представляет состояния жизненного цикла заказа

### Ключевые взаимосвязи:

1. **User - Order (Один-ко-многим)**:
    * Каждый `User` может создавать несколько сущностей `Order`
    * Каждый `Order` принадлежит одному `User`

2. **Order - Product (Один-ко-многим)**:
    * `Order` может содержать несколько записей `Product`
    * Каждый `Product` связан с одним `Order`

3. **Product - Site (Многие-к-одному)**:
    * `Product` ассоциирован с одним `Site`
    * Каждый `Site` может иметь несколько записей `Product`

4. **Order - Photo (Один-ко-многим)**:
    * `Order` может иметь несколько сущностей `Photo` для визуализации товаров
    * Каждая `Photo` принадлежит одному `Order`

5. **Order - OrderStatus (Один-ко-многим)**:
    * `Order` проходит через несколько состояний `OrderStatus`
    * Каждая запись `OrderStatus` отслеживает конкретное изменение состояния

6. **Admin - Site (Один-ко-многим)**:
    * `Admin` может управлять несколькими сущностями `Site`
    * Каждый `Site` обслуживается администраторами

### Атрибуты:

* **`User`**: `id` (PK), `telegram_id`, `username`, `full_name`, `phone_number`, `address`, `created_at`
* **`Order`**: `id` (PK), `order_number`, `total_amount`, `delivery_address`, `customer_notes`, `created_at`, `updated_at`, `user_id` (FK to User)
* **`Product`**: `id` (PK), `product_url`, `product_name`, `price`, `quantity`, `size`, `color`, `additional_notes`, `order_id` (FK to Order), `site_id` (FK to Site)
* **`Site`**: `id` (PK), `site_url`, `site_name`, `is_active`, `supported_categories`, `created_by` (FK to Admin)
* **`Photo`**: `id` (PK), `file_id`, `file_url`, `caption`, `uploaded_at`, `order_id` (FK to Order)
* **`Admin`**: `id` (PK), `telegram_id`, `username`, `full_name`, `permission_level`, `created_at`
* **`OrderStatus`**: `id` (PK), `status` (PENDING, APPROVED, PROCESSING, SHIPPED, DELIVERED, CANCELLED), `notes`, `changed_at`, `changed_by` (FK to Admin), `order_id` (FK to Order)

Данная модель обеспечивает эффективную обработку заказов, четкие взаимосвязи пользователь-заказ и комплексный административный контроль над системой заказа подарков.
