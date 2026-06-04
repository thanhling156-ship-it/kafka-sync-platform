import numpy as np
import pandas as pd
from e_com_trainingV2 import train_model

# ==========================================================
# 1. ĐỌC DỮ LIỆU TỪ FILE CSV ĐÃ TẠO
# ==========================================================
file_name = 'du_lieu_giao_dich_mo_phong.csv'
df = pd.read_csv(file_name)

X_raw = df[['price', 'quantity']].values
y_list = []
for label in df['label']:
    if label == 'normal':
        y_list.append([1, 0, 0])
    elif label == 'bulking':
        y_list.append([0, 1, 0])
    elif label == 'event':
        y_list.append([0, 0, 1])
y = np.array(y_list)

# ==========================================================
# 2. SPLIT 80/20 TRƯỚC KHI SCALE
# ==========================================================
N_total = X_raw.shape[0]
split   = int(0.8 * N_total)

X_train_raw = X_raw[:split]
X_val_raw   = X_raw[split:]
y_train     = y[:split]
y_val       = y[split:]

# ==========================================================
# 3. CHUẨN HÓA — chỉ fit trên train, apply sang val
# ==========================================================
X_min     = X_train_raw.min(axis=0)
X_max     = X_train_raw.max(axis=0)
range_val = np.where(X_max - X_min == 0, 1, X_max - X_min)

X_train_scaled = (X_train_raw - X_min) / range_val
X_val_scaled   = (X_val_raw   - X_min) / range_val  # dùng min/max của train

# ==========================================================
# 4. HUẤN LUYỆN
# ==========================================================
print(f"Đã nạp thành công {N_total} mẫu | Train: {split} | Val: {N_total - split}")
print("--> Bắt đầu chạy mạng nơ-ron lan truyền ngược...")

W1, W2, W3 = train_model(X_train_scaled, y_train,
                          X_val_scaled,   y_val,
                          learning_rate=0.05, epochs=1501)

print("\nHuấn luyện hoàn tất!")
print("W1:\n", W1)
print("W2:\n", W2)
print("W3:\n", W3)

np.savez('weights.npz', W1=W1, W2=W2, W3=W3, X_min=X_min, X_max=X_max)
print("\nĐã lưu trọng số vào 'weights.npz'.")