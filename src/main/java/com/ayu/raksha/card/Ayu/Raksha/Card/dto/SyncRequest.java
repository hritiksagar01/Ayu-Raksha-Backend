package com.ayu.raksha.card.Ayu.Raksha.Card.dto;

public class SyncRequest {
    private String accessToken;
    private String userType; // patient | doctor | uploader
    private String email;
    private String name;
    private String phone;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}

