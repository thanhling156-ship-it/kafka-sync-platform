package com.example.identity_service.entity;

//Enum giúp giới hạn tập hợp các giá trị hợp lệ, tránh việc dữ liệu rác chui vào hệ thống.
public enum UserRole {
    BASIC("ROLE_BASIC"),
    VIP("ROLE_VIP"),
    PREMIUM("ROLE_PREMIUM"),
    ADMIN("ROLE_ADMIN"),
    SHOP_OWNER("ROLE_SHOP_OWNER");

    private final String role;

    UserRole(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }
}
