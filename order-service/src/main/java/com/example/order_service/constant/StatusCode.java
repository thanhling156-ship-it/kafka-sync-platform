package com.example.order_service.constant;

public enum StatusCode {
    // 1. Khai báo các đối tượng và truyền giá trị "ruột" vào cạnh tên
    REPO_FAIL("Lỗi kho hàng"),
    PAY_FAIL("Lỗi giao dịch"),
    SHIP_SUCCESS("Thành công ( All-success )"); // Phải có dấu chấm phẩy (;) ở cuối danh sách

    // 2. Biến dùng để hứng và lưu trữ giá trị "ruột"
    private final String description;

    // 3. Constructor để gắn chuỗi vào đối tượng (chạy ngầm khi ứng dụng khởi động)
    StatusCode(String description) {
        this.description = description;
    }

    // 4. Hàm getter để bạn lôi phần "ruột" String ra sử dụng
    public String getDescription() {
        return this.description;
    }
}
