package com.example.bot;

import com.example.model.Order;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import jakarta.annotation.PostConstruct;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GiftBot extends TelegramLongPollingBot {

  @Value("${telegram.bot.token}")
  private String botToken;

  @Value("${telegram.bot.username}")
  private String botUsername;

  @Value("${admin.id}")
  private long adminId;

  private final Map<Long, Long> lastPromptMessageTime = new ConcurrentHashMap<>();
  private final Map<Long, String> userStates = new HashMap<>();
  private final Map<Long, Order> pendingOrders = new HashMap<>();
  private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

  @PostConstruct
  public void init() {
    initDatabase();
  }

  @Override
  public String getBotUsername() {
    return botUsername;
  }

  @Override
  public String getBotToken() {
    return botToken;
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (!update.hasMessage()) return;

    long chatId = update.getMessage().getChatId();
    // Проверяем getText(), а если он null, проверяем getCaption() для фото
    String messageText = update.getMessage().hasText() ? update.getMessage().getText() :
        (update.getMessage().hasPhoto() && update.getMessage().getCaption() != null ?
            update.getMessage().getCaption() : null);
    List<PhotoSize> photos = update.getMessage().hasPhoto() ? update.getMessage().getPhoto() : null;
    String username = update.getMessage().getFrom().getUserName();

    try {
      // Обработка текстовых сообщений и/или фото
      if (messageText != null || photos != null) {
        if (messageText != null) {
          System.out.println("Получен текст или подпись: " + messageText); // Логирование для отладки
          if (messageText.equals("/start") || messageText.equals("Начать")) {
            sendMessage(chatId, "Привет! Это бот для заказа товаров из Китая. Используй кнопки или команды: /order, /sites, /admin, /status");
            return;
          } else if (messageText.equals("/order") || messageText.equals("Оформить заказ")) {
            userStates.put(chatId, "awaiting_order");
            sendMessage(chatId, "Отправьте ссылку на товар, скриншот с выбранным цветом/размером/комлектацией. При необходимости укажите количество.", true);
            return;
          } else if (messageText.equals("/sites") || messageText.equals("Список сайтов и приложений")) {
            sendMessage(chatId, getSites());
            return;
          } else if (messageText.equals("/admin") || messageText.equals("Информация об админе")) {
            sendMessage(chatId, getAdminInfo());
            return;
          } else if (messageText.equals("/status") || messageText.equals("Статус моих заказов")) { // Добавляем обработку кнопки
            checkOrderStatus(chatId);
            return;
          } else if (messageText.startsWith("/addsite") && chatId == adminId) {
            handleAddSiteCommand(chatId, messageText);
            return;
          } else if (messageText.startsWith("/approve") && chatId == adminId) {
            handleApproveCommand(chatId, messageText);
            return;
          } else if (messageText.equals("/orders") && chatId == adminId) {
            handleOrdersCommand(chatId);
            return;
          } else if (messageText.equals("/clearorders") && chatId == adminId) {
            handleClearOrdersCommand(chatId);
            return;
          }
        }

        // Если пользователь находится в состоянии оформления заказа
        if (userStates.containsKey(chatId)) {
          if (userStates.get(chatId).equals("awaiting_order")) {
            if (messageText != null || photos != null) {
              handleUserState(chatId, username, messageText, photos);
            } else {
              sendMessage(chatId, "Пожалуйста, отправьте описание подарка или скриншот.", true);
            }
          } else {
            // Для других состояний (username, fio, phone, address) фото не ожидаются
            if (messageText != null) {
              handleUserState(chatId, username, messageText, null);
            } else {
              sendMessage(chatId, "Пожалуйста, отправьте текстовые данные.", true);
            }
          }
        } else {
          sendMessage(chatId, "Пожалуйста, начните оформление заказа с команды /order.");
        }
      }
    } catch (TelegramApiException e) {
      System.err.println("Ошибка Telegram API: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void initDatabase() {
    try {
      Class.forName("org.sqlite.JDBC");
      System.out.println("Драйвер SQLite успешно загружен.");
      try (Connection conn = DriverManager.getConnection("jdbc:sqlite:orders.db");
           Statement stmt = conn.createStatement()) {
        System.out.println("Подключение к базе данных успешно установлено.");

        // Проверяем, существует ли таблица orders
        ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='orders'");
        if (!rs.next()) {
          stmt.execute("""
                    CREATE TABLE orders (
                        order_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER,
                        username TEXT,
                        order_details TEXT, -- Новая колонка для хранения текста и ссылок
                        last_name TEXT,
                        first_name TEXT,
                        patronymic TEXT,
                        phone TEXT,
                        address TEXT,
                        status TEXT
                    )""");
          System.out.println("Таблица orders создана.");
        } else {
          System.out.println("Таблица orders уже существует.");

          // Проверяем, существуют ли старые колонки text и link
          rs = stmt.executeQuery("PRAGMA table_info(orders)");
          boolean hasText = false, hasLink = false;
          while (rs.next()) {
            String columnName = rs.getString("name");
            if ("text".equals(columnName)) hasText = true;
            if ("link".equals(columnName)) hasLink = true;
          }

          // Если старые колонки существуют, добавляем order_details и мигрируем данные
          if (hasText || hasLink) {
            // Добавляем новую колонку
            stmt.execute("ALTER TABLE orders ADD COLUMN order_details TEXT");
            System.out.println("Колонка order_details добавлена.");

            // Переносим данные из text и link в order_details
            stmt.execute("UPDATE orders SET order_details = COALESCE(text, '') || CASE WHEN link IS NOT NULL THEN '; ' || link ELSE '' END");
            System.out.println("Данные перенесены в order_details.");

            // Удаляем старые колонки
            if (hasText) {
              stmt.execute("ALTER TABLE orders DROP COLUMN text");
              System.out.println("Колонка text удалена.");
            }
            if (hasLink) {
              stmt.execute("ALTER TABLE orders DROP COLUMN link");
              System.out.println("Колонка link удалена.");
            }
          }
        }

        // Проверяем, существует ли таблица order_photos
        rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='order_photos'");
        if (!rs.next()) {
          stmt.execute("""
                    CREATE TABLE order_photos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        order_id INTEGER,
                        photo_file_id TEXT,
                        FOREIGN KEY (order_id) REFERENCES orders (order_id)
                    )""");
          System.out.println("Таблица order_photos создана.");
        } else {
          System.out.println("Таблица order_photos уже существует.");
        }

        // Проверяем, существует ли столбец photo_file_id в таблице orders
        rs = stmt.executeQuery("PRAGMA table_info(orders)");
        boolean hasPhotoFileId = false;
        while (rs.next()) {
          if ("photo_file_id".equals(rs.getString("name"))) {
            hasPhotoFileId = true;
            break;
          }
        }
        // Если столбец photo_file_id существует, удалим его
        if (hasPhotoFileId) {
          stmt.execute("ALTER TABLE orders DROP COLUMN photo_file_id");
          System.out.println("Столбец photo_file_id удалён из таблицы orders.");
        } else {
          System.out.println("Столбец photo_file_id в таблице orders отсутствует.");
        }

        // Проверяем, существует ли таблица sites
        rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='sites'");
        if (!rs.next()) {
          stmt.execute("""
                    CREATE TABLE sites (
                        site_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        url TEXT,
                        description TEXT
                    )""");
          System.out.println("Таблица sites создана.");
        } else {
          System.out.println("Таблица sites уже существует.");
        }

        // Проверяем, существует ли таблица admins
        rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='admins'");
        if (!rs.next()) {
          stmt.execute("""
                    CREATE TABLE admins (
                        admin_id INTEGER PRIMARY KEY,
                        info TEXT
                    )""");
          System.out.println("Таблица admins создана.");
        } else {
          System.out.println("Таблица admins уже существует.");
        }
      }
    } catch (ClassNotFoundException e) {
      System.err.println("Драйвер SQLite не найден: " + e.getMessage());
      e.printStackTrace();
    } catch (SQLException e) {
      System.err.println("Ошибка при создании таблиц: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void sendMessage(long chatId, String text, boolean showNextButton) throws TelegramApiException {
    SendMessage message = new SendMessage();
    message.setChatId(String.valueOf(chatId));
    message.setText(text);

    ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
    keyboard.setResizeKeyboard(true);
    List<KeyboardRow> rows = new ArrayList<>();

    if (showNextButton) {
      KeyboardRow actionRow = new KeyboardRow();
      actionRow.add("Далее");
      actionRow.add("Отмена"); // Добавляем кнопку "Отмена"
      rows.add(actionRow);
    } else {
      KeyboardRow row = new KeyboardRow();
      row.add("Оформить заказ");
      row.add("Список сайтов и приложений");
      rows.add(row);
      row = new KeyboardRow();
      row.add("Информация об админе ");
      row.add("Статус моих заказов");
      rows.add(row);
    }

    keyboard.setKeyboard(rows);
    message.setReplyMarkup(keyboard);

    executeAsync(message).exceptionally(throwable -> {
      System.err.println("Ошибка отправки сообщения: " + throwable.getMessage());
      throwable.printStackTrace();
      return null;
    }).join();
  }

  private void sendMessage(long chatId, String text) throws TelegramApiException {
    sendMessage(chatId, text, false);
  }

  // Модифицированный метод handleUserState
  private void handleUserState(long chatId, String username, String messageText, List<PhotoSize> photos) throws TelegramApiException {
    String state = userStates.get(chatId);
    if (state == null) return;

    // Обработка кнопки "Отмена"
    if (messageText != null && messageText.equals("Отмена")) {
      userStates.remove(chatId);
      pendingOrders.remove(chatId);
      sendMessage(chatId, "Оформление заказа отменено. Используйте /order, чтобы начать заново.");
      return;
    }

    if (state.equals("awaiting_order")) {
      Order order = pendingOrders.computeIfAbsent(chatId, k -> new Order());
      System.out.println("Начало handleUserState для chatId " + chatId + ", текущее значение orderDetails: " + order.getOrderDetails());

      boolean hasInput = false;

      if (messageText != null && !messageText.equals("Далее")) {
        // Сохраняем текст сообщения как есть
        if (order.getOrderDetails() == null) {
          order.setOrderDetails(messageText);
        } else {
          order.setOrderDetails(order.getOrderDetails() + "; " + messageText);
        }
        hasInput = true;
        System.out.println("После обработки текста/подписи для chatId " + chatId + ": orderDetails = " + order.getOrderDetails());
      }

      if (photos != null && !photos.isEmpty()) {
        PhotoSize largestPhoto = photos.get(photos.size() - 1);
        String photoFileId = largestPhoto.getFileId();
        order.addPhotoFileId(photoFileId);
        if (order.getOrderDetails() == null) {
          order.setOrderDetails("Заказ со скриншотом");
        }
        hasInput = true;
        System.out.println("После обработки фото для chatId " + chatId + ": orderDetails = " + order.getOrderDetails());
      }

      if (hasInput) {
        long currentTime = System.currentTimeMillis();
        Long lastSentTime = lastPromptMessageTime.getOrDefault(chatId, 0L);
        if (currentTime - lastSentTime > 1000){
        sendMessage(chatId, "Вы можете добавить ещё описание, ссылку или скриншот, или нажать 'Далее' для продолжения.", true);
          lastPromptMessageTime.put(chatId, currentTime);
          System.out.println("Отправлено сообщение для chatId " + chatId + " в " + currentTime);
        } else {
          System.out.println("Пропущена отправка для chatId " + chatId + ": слишком скоро");
        }
      }

      if (messageText != null && messageText.equals("Далее")) {
        if (order.getOrderDetails() == null && order.getPhotoFileIds().isEmpty()) {
          sendMessage(chatId, "Пожалуйста, отправьте описание подарка, ссылку или скриншот перед тем, как продолжить.", true);
          return;
        }
        userStates.put(chatId, "awaiting_username");
        sendMessage(chatId, "Теперь введите ваш Telegram username (например, @kitau123):", true);
        System.out.println("После нажатия 'Далее' для chatId " + chatId + ": orderDetails = " + order.getOrderDetails());
      }

    } else if (state.equals("awaiting_username")) {
      Order order = pendingOrders.get(chatId);
      System.out.println("Состояние awaiting_username для chatId " + chatId + ": orderDetails = " + order.getOrderDetails());
      if (messageText != null && messageText.startsWith("@")) {
        order.setUsername(messageText.trim());
        userStates.put(chatId, "awaiting_fio");
        sendMessage(chatId, "Введите фамилию, имя и отчество, в одной строке через пробел (например, Иванов Иван Иванович):", true);
      } else {
        sendMessage(chatId, "Username должен начинаться с @. Попробуйте снова.", true);
      }

    } else if (state.equals("awaiting_fio")) {
      Order order = pendingOrders.get(chatId);
      System.out.println("Состояние awaiting_fio для chatId " + chatId + ": orderDetails = " + order.getOrderDetails());
      String[] nameParts = messageText.trim().split("\\s+");
      if (nameParts.length >= 3) {
        order.setLastName(nameParts[0]);
        order.setFirstName(nameParts[1]);
        order.setPatronymic(nameParts[2]);
        userStates.put(chatId, "awaiting_phone");
        sendMessage(chatId, "Теперь введите номер телефона без пробелов (например, +375441314715):", true);
      } else {
        sendMessage(chatId, "Пожалуйста, укажите ФИО через пробел: Фамилия Имя Отчество", true);
      }

    } else if (state.equals("awaiting_phone")) {
      Order order = pendingOrders.get(chatId);
      System.out.println("Состояние awaiting_phone для chatId " + chatId + ": orderDetails = " + order.getOrderDetails());
      String phoneNumber = messageText.trim().replaceAll("\\s+", "");
      if (phoneNumber.matches("^\\+375\\d{9}$")) {
        order.setPhone(phoneNumber);
        userStates.put(chatId, "awaiting_address");
        sendMessage(chatId, "Теперь укажите способ получения товара:\n1. Самовывоз г.Барановичи\n2. Доставка на ПВЗ (укажите ваш номер отделения и полный адрес ПВЗ)\np.s (ПВЗ - пункт выдачи заказов)", true);
      } else {
        sendMessage(chatId, "Номер телефона должен начинаться с +375 и содержать ровно 12 цифр (например, +375441314715). Пожалуйста, введите корректный номер:", true);
      }
    } else if (state.equals("awaiting_address")) {
      Order order = pendingOrders.get(chatId);
      System.out.println("Состояние awaiting_address для chatId " + chatId + ": orderDetails = " + order.getOrderDetails());
      order.setAddress(messageText.trim());
      saveOrder(order, chatId);
      userStates.remove(chatId);
      pendingOrders.remove(chatId);

      sendMessage(chatId, "Ваш заказ №" + order.getOrderId() + " в обработке!");

      System.out.println("Перед отправкой админу для chatId " + chatId + ": orderDetails = " + order.getOrderDetails());
      StringBuilder adminMessage = new StringBuilder("Новый заказ №" + order.getOrderId() + " от " + order.getUsername() + ":\n" +
          "Детали заказа: " + (order.getOrderDetails() != null ? order.getOrderDetails() : "Отсутствует") + "\n" +
          "Фамилия: " + order.getLastName() + "\n" +
          "Имя: " + order.getFirstName() + "\n" +
          "Отчество: " + order.getPatronymic() + "\n" +
          "Телефон: " + order.getPhone() + "\n" +
          "Доставка: " + order.getAddress());
      if (!order.getPhotoFileIds().isEmpty()) {
        adminMessage.append("\nСкриншоты: [фото отправлены]");
        for (String photoFileId : order.getPhotoFileIds()) {
          SendPhoto photoMessage = new SendPhoto();
          photoMessage.setChatId(String.valueOf(adminId));
          photoMessage.setPhoto(new InputFile(photoFileId));
          executeAsync(photoMessage).exceptionally(throwable -> {
            System.err.println("Ошибка отправки скриншота админу: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
          }).join();
        }
      }
      System.out.println("Сообщение для админа: " + adminMessage.toString());
      sendMessage(adminId, adminMessage.toString());
    }
  }


  private void saveOrder(Order order, long chatId) {
    String sqlOrder = "INSERT INTO orders (user_id, username, order_details, last_name, first_name, patronymic, phone, address, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    String sqlPhoto = "INSERT INTO order_photos (order_id, photo_file_id) VALUES (?, ?)";
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:orders.db");
         PreparedStatement pstmtOrder = conn.prepareStatement(sqlOrder, Statement.RETURN_GENERATED_KEYS)) {
      pstmtOrder.setLong(1, chatId);
      pstmtOrder.setString(2, order.getUsername());
      pstmtOrder.setString(3, order.getOrderDetails());
      pstmtOrder.setString(4, order.getLastName());
      pstmtOrder.setString(5, order.getFirstName());
      pstmtOrder.setString(6, order.getPatronymic());
      pstmtOrder.setString(7, order.getPhone());
      pstmtOrder.setString(8, order.getAddress());
      pstmtOrder.setString(9, "в обработке");
      pstmtOrder.executeUpdate();

      try (ResultSet rs = pstmtOrder.getGeneratedKeys()) {
        if (rs.next()) {
          int orderId = rs.getInt(1);
          order.setOrderId(orderId);

          // Сохраняем фотографии в таблицу order_photos
          if (!order.getPhotoFileIds().isEmpty()) {
            try (PreparedStatement pstmtPhoto = conn.prepareStatement(sqlPhoto)) {
              for (String photoFileId : order.getPhotoFileIds()) {
                pstmtPhoto.setInt(1, orderId);
                pstmtPhoto.setString(2, photoFileId);
                pstmtPhoto.executeUpdate();
              }
            }
          }
        }
      }
    } catch (SQLException e) {
      System.err.println("Ошибка при сохранении заказа: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private String getSites() {
    StringBuilder sites = new StringBuilder("Список сайтов и приложений:\n");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:orders.db");
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT description, url FROM sites")) {  // Изменил порядок полей в запросе
      int index = 1;
      while (rs.next()) {
        sites.append(index++).append(". ").append(rs.getString("description"))
             .append(" - ").append(rs.getString("url")).append("\n");
      }
    } catch (SQLException e) {
      System.err.println("Ошибка при получении списка сайтов: " + e.getMessage());
      return "Ошибка при получении списка сайтов.";
    }
    return sites.length() > "Список сайтов и приложений:\n".length() ? sites.toString() : "Сайты пока не добавлены.";
  }

  private String getAdminInfo() {
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:orders.db");
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT info FROM admins WHERE admin_id = " + adminId)) {
      if (rs.next()) {
        return rs.getString("info");
      }
    } catch (SQLException e) {
      System.err.println("Ошибка при получении информации об админе: " + e.getMessage());
      e.printStackTrace();
    }
    return "Админ: @dst13072000\nКонтакты: +375292572665";
  }

  private void handleAddSiteCommand(long chatId, String messageText) throws TelegramApiException {
    String[] parts = messageText.split(" ", 3);
    if (parts.length == 3) {
      try (Connection conn = DriverManager.getConnection("jdbc:sqlite:orders.db");
           PreparedStatement pstmt = conn.prepareStatement("INSERT INTO sites (description, url) VALUES (?, ?)")) {
        pstmt.setString(1, parts[1]);  // Теперь description идет первым
        pstmt.setString(2, parts[2]);  // А url вторым
        pstmt.executeUpdate();
        sendMessage(chatId, "Сайт добавлен!");
      } catch (SQLException e) {
        System.err.println("Ошибка при добавлении сайта: " + e.getMessage());
        sendMessage(chatId, "Ошибка при добавлении сайта: " + e.getMessage());
        e.printStackTrace();
      }
    } else {
      sendMessage(chatId, "Используй: /addsite <описание> <url>");
    }
  }
  // Новый метод для очистки базы данных заказов
  private void handleClearOrdersCommand(long chatId) throws TelegramApiException {
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:orders.db")) {
      // Очищаем таблицу order_photos
      try (Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("DELETE FROM order_photos");
        System.out.println("Таблица order_photos очищена.");
      }

      // Очищаем таблицу orders
      try (Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("DELETE FROM orders");
        System.out.println("Таблица orders очищена.");
      }

      // Сбрасываем счетчик автоинкремента в таблице orders
      try (Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("DELETE FROM sqlite_sequence WHERE name='orders'");
        System.out.println("Счетчик автоинкремента для orders сброшен.");
      }

      sendMessage(chatId, "База данных заказов успешно очищена.");
    } catch (SQLException e) {
      System.err.println("Ошибка при очистке базы данных заказов: " + e.getMessage());
      e.printStackTrace();
      sendMessage(chatId, "Ошибка при очистке базы данных заказов: " + e.getMessage());
    }
  }

  private void handleApproveCommand(long chatId, String messageText) throws TelegramApiException {
    String[] parts = messageText.split(" ");
    if (parts.length == 2) {
      try {
        int orderId = Integer.parseInt(parts[1]);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:orders.db");
             PreparedStatement pstmt = conn.prepareStatement("UPDATE orders SET status = ? WHERE order_id = ?")) {
          pstmt.setString(1, "одобрен");
          pstmt.setInt(2, orderId);
          int rowsAffected = pstmt.executeUpdate();
          if (rowsAffected > 0) {
            try (PreparedStatement pstmt2 = conn.prepareStatement("SELECT user_id FROM orders WHERE order_id = ?")) {
              pstmt2.setInt(1, orderId);
              try (ResultSet rs = pstmt2.executeQuery()) {
                if (rs.next()) {
                  long userId = rs.getLong("user_id");
                  sendMessage(userId, "Ваш заказ №" + orderId + " одобрен!");
                  sendMessage(chatId, "Заказ №" + orderId + " успешно одобрен.");
                }
              }
            }
          } else {
            sendMessage(chatId, "Заказ №" + orderId + " не найден.");
          }
        }
      } catch (NumberFormatException e) {
        sendMessage(chatId, "Неверный формат номера заказа. Используй: /approve <order_id>");
      } catch (SQLException e) {
        System.err.println("Ошибка при одобрении заказа: " + e.getMessage());
        sendMessage(chatId, "Ошибка при одобрении заказа: " + e.getMessage());
        e.printStackTrace();
      }
    } else {
      sendMessage(chatId, "Используй: /approve <order_id>");
    }
  }

  private void checkOrderStatus(long chatId) throws TelegramApiException {
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:orders.db");
         PreparedStatement stmt = conn.prepareStatement("SELECT order_id, status FROM orders WHERE user_id = ?")) {
      stmt.setLong(1, chatId);
      try (ResultSet rs = stmt.executeQuery()) {
        StringBuilder response = new StringBuilder("Ваши заказы:\n");
        boolean hasOrders = false;
        while (rs.next()) {
          hasOrders = true;
          response.append("Заказ №").append(rs.getInt("order_id"))
                  .append(": ").append(rs.getString("status")).append("\n");
        }
        sendMessage(chatId, hasOrders ? response.toString() : "У вас нет заказов.");
      }
    } catch (SQLException e) {
      System.err.println("Ошибка при проверке статуса: " + e.getMessage());
      sendMessage(chatId, "Ошибка при проверке статуса: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private String getFilePath(String fileId) {
    try {
      GetFile getFile = new GetFile();
      getFile.setFileId(fileId);
      org.telegram.telegrambots.meta.api.objects.File telegramFile = execute(getFile);
      return telegramFile.getFilePath();
    } catch (TelegramApiException e) {
      System.err.println("Ошибка при получении file_path для fileId " + fileId + ": " + e.getMessage());
      e.printStackTrace();
      return "";
    }
  }

  private void handleOrdersCommand(long chatId) throws TelegramApiException {
    Workbook workbook = null;
    java.io.File excelFile = null;
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:orders.db");
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM orders ORDER BY username COLLATE NOCASE")) {
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
      excelFile = new java.io.File("orders_table_" + timestamp + ".xlsx");
      workbook = new XSSFWorkbook();
      Sheet sheet = workbook.createSheet("Заказы");

      // Создаем стили для цветного фона
      CellStyle greenStyle = workbook.createCellStyle();
      greenStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
      greenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      CellStyle redStyle = workbook.createCellStyle();
      redStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
      redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      // Создаем стиль для гиперссылок
      CellStyle hyperlinkStyle = workbook.createCellStyle();
      Font hyperlinkFont = workbook.createFont();
      hyperlinkFont.setUnderline(Font.U_SINGLE);
      hyperlinkFont.setColor(IndexedColors.BLUE.getIndex());
      hyperlinkStyle.setFont(hyperlinkFont);

      // Заголовки столбцов
      String[] headers = {"Статус одобрения", "ID", "Username", "Детали заказа", "Скриншот", "Имя", "Фамилия", "Отчество", "Телефон", "Адрес", "Статус"};
      Row headerRow = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
      }
      sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

      // Устанавливаем ширину столбцов
      sheet.setColumnWidth(0, 20 * 256); // Статус одобрения
      sheet.setColumnWidth(1, 10 * 256); // ID
      sheet.setColumnWidth(2, 20 * 256); // Username
      sheet.setColumnWidth(3, 40 * 256); // Детали заказа (увеличиваем ширину для длинного текста)
      sheet.setColumnWidth(4, 20 * 256); // Скриншот
      sheet.setColumnWidth(5, 20 * 256); // Имя
      sheet.setColumnWidth(6, 20 * 256); // Фамилия
      sheet.setColumnWidth(7, 20 * 256); // Отчество
      sheet.setColumnWidth(8, 20 * 256); // Телефон
      sheet.setColumnWidth(9, 30 * 256); // Адрес
      sheet.setColumnWidth(10, 20 * 256); // Статус

      // Заполняем таблицу данными
      boolean hasOrders = false;
      int rowNum = 1;
      while (rs.next()) {
        hasOrders = true;
        int orderId = rs.getInt("order_id");

        // Загружаем file_id фотографий
        List<String> photoFileIds = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT photo_file_id FROM order_photos WHERE order_id = ?")) {
          pstmt.setInt(1, orderId);
          try (ResultSet photoRs = pstmt.executeQuery()) {
            while (photoRs.next()) {
              String photoFileId = photoRs.getString("photo_file_id");
              if (photoFileId != null && !photoFileId.isEmpty()) {
                photoFileIds.add(photoFileId);
              }
            }
          }
        } catch (SQLException e) {
          System.err.println("Ошибка при загрузке фотографий для заказа №" + orderId + ": " + e.getMessage());
          e.printStackTrace();
        }
        System.out.println("Найдено " + photoFileIds.size() + " фотографий для заказа №" + orderId + ": " + photoFileIds);

        String status = rs.getString("status") != null ? rs.getString("status") : "в обработке";

        // Первая строка заказа с основными данными
        int startRow = rowNum;
        Row row = sheet.createRow(rowNum++);

        // Столбец "Статус одобрения"
        Cell approvalCell = row.createCell(0);
        approvalCell.setCellValue(status.equals("одобрен") ? "Одобрен" : "Не одобрен");
        approvalCell.setCellStyle(status.equals("одобрен") ? greenStyle : redStyle);

        // Остальные столбцы
        row.createCell(1).setCellValue(orderId);
        row.createCell(2).setCellValue(rs.getString("username") != null ? rs.getString("username") : "");
        row.createCell(3).setCellValue(rs.getString("order_details") != null ? rs.getString("order_details") : "");
        row.createCell(5).setCellValue(rs.getString("first_name") != null ? rs.getString("first_name") : "");
        row.createCell(6).setCellValue(rs.getString("last_name") != null ? rs.getString("last_name") : "");
        row.createCell(7).setCellValue(rs.getString("patronymic") != null ? rs.getString("patronymic") : "");
        row.createCell(8).setCellValue(rs.getString("phone") != null ? rs.getString("phone") : "");
        row.createCell(9).setCellValue(rs.getString("address") != null ? rs.getString("address") : "");
        row.createCell(10).setCellValue(status);

        // Столбец "Скриншот" — добавляем гиперссылки
        if (!photoFileIds.isEmpty()) {
          int photoIndex = 1;
          // Добавляем первый скриншот в текущую строку
          String filePath = getFilePath(photoFileIds.get(0));
          if (!filePath.isEmpty()) {
            String photoUrl = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
            Cell photoCell = row.createCell(4);
            photoCell.setCellValue("Скриншот " + photoIndex);
            photoCell.setHyperlink(new XSSFHyperlink(HyperlinkType.URL) {{
              setAddress(photoUrl);
            }});
            photoCell.setCellStyle(hyperlinkStyle);
          } else {
            row.createCell(4).setCellValue("Скриншот " + photoIndex + ": Ошибка");
          }
          photoIndex++;

          // Добавляем остальные скриншоты в новые строки
          for (int i = 1; i < photoFileIds.size(); i++) {
            row = sheet.createRow(rowNum++);
            filePath = getFilePath(photoFileIds.get(i));
            if (!filePath.isEmpty()) {
              String photoUrl = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
              Cell photoCell = row.createCell(4);
              photoCell.setCellValue("Скриншот " + photoIndex);
              photoCell.setHyperlink(new XSSFHyperlink(HyperlinkType.URL) {{
                setAddress(photoUrl);
              }});
              photoCell.setCellStyle(hyperlinkStyle);
            } else {
              row.createCell(4).setCellValue("Скриншот " + photoIndex + ": Ошибка");
            }
            photoIndex++;
          }

          // Объединяем ячейки для остальных столбцов
          if (photoFileIds.size() > 1) {
            for (int col = 0; col <= 10; col++) {
              if (col != 4) { // Пропускаем столбец "Скриншот"
                sheet.addMergedRegion(new CellRangeAddress(startRow, rowNum - 1, col, col));
              }
            }
          }
        } else {
          row.createCell(4).setCellValue("");
          System.out.println("Фотографии отсутствуют для заказа №" + orderId);
        }
      }

      if (!hasOrders) {
        sendMessage(chatId, "Заказы отсутствуют.");
        return;
      }

      // Автоподбор ширины столбцов для текстовых данных
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }

      // Сохраняем Excel-файл
      try (FileOutputStream fileOut = new FileOutputStream(excelFile)) {
        workbook.write(fileOut);
        fileOut.flush();
      } catch (IOException e) {
        System.err.println("Ошибка при записи Excel-файла: " + e.getMessage());
        e.printStackTrace();
        sendMessage(chatId, "Ошибка при сохранении таблицы заказов: " + e.getMessage());
        return;
      }

      // Отправляем файл админу
      SendDocument document = new SendDocument();
      document.setChatId(String.valueOf(chatId));
      document.setDocument(new InputFile(excelFile));
      document.setCaption("Таблица заказов (Excel)");
      executeAsync(document).exceptionally(throwable -> {
        System.err.println("Ошибка отправки документа: " + throwable.getMessage());
        throwable.printStackTrace();
        return null;
      }).join();

    } catch (SQLException e) {
      System.err.println("Ошибка при получении заказов из базы данных: " + e.getMessage());
      sendMessage(chatId, "Ошибка при получении заказов из базы данных: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      System.err.println("Неизвестная ошибка при создании таблицы заказов: " + e.getMessage());
      sendMessage(chatId, "Неизвестная ошибка при создании таблицы заказов: " + e.getMessage());
      e.printStackTrace();
    } finally {
      // Закрываем workbook
      if (workbook != null) {
        try {
          workbook.close();
        } catch (IOException e) {
          System.err.println("Ошибка при закрытии workbook: " + e.getMessage());
          e.printStackTrace();
        }
      }
      // Удаляем Excel-файл
      if (excelFile != null && excelFile.exists()) {
        try {
          if (!excelFile.delete()) {
            System.err.println("Не удалось удалить файл: " + excelFile.getName());
          }
        } catch (Exception e) {
          System.err.println("Ошибка при удалении Excel-файла: " + e.getMessage());
          e.printStackTrace();
        }
      }
    }
  }
}