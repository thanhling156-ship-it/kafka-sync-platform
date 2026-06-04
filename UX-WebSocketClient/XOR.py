import numpy as np
import matplotlib.pyplot as plt

# 4 điểm dữ liệu gốc (Kích thước 4x3, với cột cuối là bias)
X = np.array([
    [0, 0, 1],
    [0, 1, 1],
    [1, 0, 1],
    [1, 1, 1]
])

# Cố định seed để mỗi lần chạy đều ra cùng một kết quả ngẫu nhiên
np.random.seed(42)

# W1: Kích thước 3x3 (Cho lớp ẩn 1)
W1 = np.random.randn(3, 3) * 0.5

# W2: Kích thước 3x3 (Cho lớp ẩn 2)
W2 = np.random.randn(3, 3) * 0.5

# W3: Kích thước 3x1 (Cho lớp đầu ra)
W3 = np.random.randn(3, 1) * 0.5

# Nhãn tương ứng của XOR (Kích thước 4x1)
y = np.array([
    [0],
    [1],
    [1],
    [0]
])

learning_rate = 0.1

for i in range(1001):
    # Forward pass
    Z1 = X @ W1
    H = Z1.copy()
    H[:, :2] = np.maximum(0, Z1[:, :2])  # Chỉ ReLU 2 cột đầu

    Z2 = H @ W2
    Q = Z2.copy()
    Q[:, :2] = np.maximum(0, Z2[:, :2])  # Chỉ ReLU 2 cột đầu

    L = Q @ W3  # Output layer (Linear activation)

    # Bước 1 & 2: Hiệu số và bình phương từng phần tử
    squared_diff = (L - y) ** 2

    # Bước 3 & 4: Tính tổng bằng ma trận số 1, rồi lấy trung bình để ra sai số E
    E = np.mean(squared_diff)

    if i % 100 == 0:
        print(f"Iteration {i}, Loss: {E:.4f}")

    # Backward pass    
    # E = 1/4 nhân sigma (Li-yi)^2; thì với hàng thứ i, thì đạo hàm là 1/2 nhân Li-yi
    dE_dL = (0.5 / X.shape[0]) * (L - y)  # Kích thước 4x1 # X.shape[0] là số lượng mẫu (4) để chia đều lỗi cho từng mẫu
    dL_dQ = W3.T  # Kích thước 1x3
    dE_dQ = dE_dL @ dL_dQ  # Kích thước 4x3
    # Chỉ nhân đạo hàm ReLU (0 hoặc 1) cho 2 cột đầu, cột 3 giữ nguyên lỗi truyền về (nhân với 1)
    dE_dZ2 = dE_dQ.copy()
    dE_dZ2[:, :2] = dE_dQ[:, :2] * np.where(Z2[:, :2] <= 0, 0, 1)
    # ReLU chỉ có ý nghĩa khi áp vào 1 ma trận => k thể tính riêng => phải gom kết quả về được trước khi đạo hàm ReLU; vị trí nào <= 0 ở Z2 thì đạo hàm ReLU sẽ là 0, ngược lại là 1 

    dZ2_dH = W2.T  # Kích thước 3x3
    dE_dH = dE_dZ2 @ dZ2_dH  # Kích thước 4x3
    dE_dZ1 = dE_dH.copy()
    dE_dZ1[:, :2] = dE_dH[:, :2] * np.where(Z1[:, :2] <= 0, 0, 1)

    dZ1_dW1 = X.T  # Kích thước 3x4
    dW1 = dZ1_dW1 @ dE_dZ1  # Kích thước 3x3

    dZ2_dW2 = H.T  # Kích thước 3x4
    dW2 = dZ2_dW2 @ dE_dZ2  # Kích thước 3x3

    dL_dW3 = Q.T  # Kích thước 3x4
    dW3 = dL_dW3 @ dE_dL  # Kích thước 3x1

    # 1. Định nghĩa Delta mang dấu trừ do là lựa chọn trái dấu với đạo hàm để giảm sai số
    deltaW1 = -learning_rate * dW1
    deltaW2 = -learning_rate * dW2
    deltaW3 = -learning_rate * dW3

    # 2. Cập nhật trọng số bằng phép CỘNG tổng quát
    W1 += deltaW1
    W2 += deltaW2
    W3 += deltaW3
