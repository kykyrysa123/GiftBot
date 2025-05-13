package com.example.model;

import java.util.ArrayList;
import java.util.List;

public class Order {
  private int orderId;
  private String username;
  private String text;
  private String link;
  private String lastName;
  private String firstName;
  private String patronymic;
  private String phone;
  private String address;
  private List<String> photoFileIds = new ArrayList<>();

  public void setOrderId(int orderId) {
    this.orderId = orderId;
  }

  public int getOrderId() {
    return orderId;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getUsername() {
    return username;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getText() {
    return text;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getLink() {
    return link;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setPatronymic(String patronymic) {
    this.patronymic = patronymic;
  }

  public String getPatronymic() {
    return patronymic;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getPhone() {
    return phone;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getAddress() {
    return address;
  }

  public void addPhotoFileId(String photoFileId) {
    this.photoFileIds.add(photoFileId);
  }

  public List<String> getPhotoFileIds() {
    return photoFileIds;
  }
}