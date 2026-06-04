import numpy as np

def train_model(X_raw, y, learning_rate=0.01, epochs=5001):
    np.random.seed(42)
    N = X_raw.shape[0]  # Số lượng mẫu

    # Tạo ma trận X mới bằng cách chèn cột hằng số 1 vào bên phải X_raw để làm Bias cho lớp ẩn 1
    X = np.hstack([X_raw, np.ones((N, 1))])  # Kích thước: (N x input_dim)

    input_dim = X.shape[1]    # Số lượng đặc trưng (đã bao gồm cột bias)
    size_label = y.shape[1]   # Số lượng lớp (số cột của y)
    
    # --- ĐỔI CẤU HÌNH TẠI ĐÂY: TĂNG LÊN 16 NEURON & DÙNG HE INITIALIZATION ---
    # Thay vì nhân 0.5, dùng công thức căn bậc hai của (2 / số node đầu vào)
    W1 = np.random.randn(input_dim, 16) * np.sqrt(2.0 / input_dim)  # Kích thước: (input_dim x 16)
    W2 = np.random.randn(17, 16) * np.sqrt(2.0 / 17)                # Kích thước: (17 x 16) - 16 node + 1 bias
    W3 = np.random.randn(16, size_label) * np.sqrt(2.0 / 16)         # Kích thước: (16 x size_label)

    for i in range(epochs):
        # ==========================================
        # 1. FORWARD PASS (Lan truyền xuôi)
        # ==========================================
        
        # Lớp ẩn 1: Kích thước Z1 là (N x 16)
        Z1 = X @ W1
        H_relu = np.maximum(0, Z1)  
        # Thêm cột bias vào H; Kích thước mới: (N x 17)
        H = np.hstack([H_relu, np.ones((N, 1))])  
        
        # Lớp ẩn 2: Kích thước Z2 là (N x 16)
        Z2 = H @ W2
        Q_relu = np.maximum(0, Z2)  
        Q = Q_relu  # Kích thước: (N x 16)

        # Lớp đầu ra: Kích thước L là (N x size_label)
        L = Q @ W3  
        shift_x = L - np.max(L, axis=1, keepdims=True)
        A3 = np.exp(shift_x) / np.sum(np.exp(shift_x), axis=1, keepdims=True)  

        # Tính Categorical Cross-Entropy Loss
        epsilon = 1e-15
        A3_clipped = np.clip(A3, epsilon, 1 - epsilon)  
        E = -np.sum(y * np.log(A3_clipped)) / N

        if i % 100 == 0:
            print(f"Iteration {i}, Loss: {E:.4f}")

        # ==========================================
        # 2. BACKWARD PASS (Lan truyền ngược)
        # ==========================================
        
        dE_dL = (A3 - y) / N                            # Kích thước: (N x size_label)
        
        # --- TẦNG ĐẦU RA W3 ---
        # (N x size_label) @ (size_label x 16) -> (N x 16)
        dE_dQ = dE_dL @ W3.T                            
        dE_dZ2 = dE_dQ * np.where(Z2 <= 0, 0, 1)        # Kích thước: (N x 16)
        
        # --- TẦNG ẨN 2 W2 ---
        # CHUẨN XÁC: Bỏ hàng bias (hàng thứ 17) của W2 khi nhân ngược
        # (N x 16) @ (16 x 16) -> (N x 16). Kết quả tự về đúng cỡ của 16 neuron thực sự!
        dE_dH = dE_dZ2 @ W2[:16, :].T
        dE_dZ1 = dE_dH * np.where(Z1 <= 0, 0, 1)        # Kích thước: (N x 16) 

        # ==========================================
        # 3. TÍNH GRADIENT VÀ CẬP NHẬT TRỌNG SỐ
        # ==========================================
        
        # Kích thước tự động khớp theo cấu hình mới:
        dW1 = X.T @ dE_dZ1              # (input_dim x N) @ (N x 16) -> (input_dim x 16)
        dW2 = H.T @ dE_dZ2              # (17 x N) @ (N x 16) -> (17 x 16)
        dW3 = Q.T @ dE_dL               # (16 x N) @ (N x size_label) -> (16 x size_label)

        # Cập nhật trọng số
        W1 -= learning_rate * dW1
        W2 -= learning_rate * dW2
        W3 -= learning_rate * dW3

    return W1, W2, W3