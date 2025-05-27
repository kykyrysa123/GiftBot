package com.example.model;

import java.util.ArrayList;
import java.util.List;

public class Order {
  private int orderId;
  private String username;
  private String orderDetails; // Новое поле для текста и ссылок
  private String lastName;
  private String firstName;
  private String patronymic;
  private String phone;
  private String address;
  private List<String> photoFileIds = new ArrayList<>();

  // Геттеры и сеттеры
  public int getOrderId() {
    return orderId;
  }

  public void setOrderId(int orderId) {
    this.orderId = orderId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getOrderDetails() {
    return orderDetails;
  }

  public void setOrderDetails(String orderDetails) {
    this.orderDetails = orderDetails;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getPatronymic() {
    return patronymic;
  }

  public void setPatronymic(String patronymic) {
    this.patronymic = patronymic;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public List<String> getPhotoFileIds() {
    return photoFileIds;
  }

  public void addPhotoFileId(String photoFileId) {
    this.photoFileIds.add(photoFileId);
  }
}