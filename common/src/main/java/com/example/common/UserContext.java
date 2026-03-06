package com.example.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserContext {
    // ThreadLocal giống như một "ngăn tủ riêng" cho mỗi Request
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();
    private static final ThreadLocal<String> currentRole = new ThreadLocal<>();

    // Hàm NẠP dữ liệu (Bạn đang thiếu cái này)
    public static void setCurrentUser(String username) {
        currentUser.set(username);
    }

    public static void setCurrentRole(String role) {
        currentRole.set(role);
    }

    // Hàm LẤY dữ liệu
    public static String getCurrentUser() {
        return currentUser.get();
    }

    public static String getCurrentRole() {
        return currentRole.get();
    }

    // Hàm DỌN DẸP (Cực kỳ quan trọng để tránh lộ dữ liệu)
    public static void clear() {
        currentUser.remove();
        currentRole.remove();
    }
}