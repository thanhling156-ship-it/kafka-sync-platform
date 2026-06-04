import numpy as np
import pandas as pd

# ==========================================================
# 1. TẢI TRỌNG SỐ VÀ DỮ LIỆU
# ==========================================================
saved_data = np.load('weights.npz')
W1    = saved_data['W1']
W2    = saved_data['W2']
W3    = saved_data['W3']
X_min = saved_data['X_min']
X_max = saved_data['X_max']

labels = ['normal', 'bulking', 'event']

df     = pd.read_csv('du_lieu_giao_dich_mo_phong.csv')
N      = len(df)
split  = int(0.8 * N)
df_val = df.iloc[split:].reset_index(drop=True)  # 120 mẫu cuối

X_val_raw = df_val[['price', 'quantity']].values
range_val  = np.where(X_max - X_min == 0, 1, X_max - X_min)
X_val      = (X_val_raw - X_min) / range_val

# Ground truth
y_true = df_val['label'].values

# ==========================================================
# 2. FORWARD PASS TRÊN 120 MẪU
# ==========================================================
X_in   = np.hstack([X_val, np.ones((len(X_val), 1))])
Z1     = X_in @ W1
H      = np.hstack([np.maximum(0, Z1), np.ones((len(X_val), 1))])
Z2     = H @ W2
Q      = np.maximum(0, Z2)
L      = Q @ W3
shift  = L - np.max(L, axis=1, keepdims=True)
probs  = np.exp(shift) / np.sum(np.exp(shift), axis=1, keepdims=True)

pred_idx    = np.argmax(probs, axis=1)
pred_labels = [labels[i] for i in pred_idx]
accuracy    = np.mean([p == t for p, t in zip(pred_labels, y_true)])

# ==========================================================
# 3. IN KẾT QUẢ
# ==========================================================
print(f"Accuracy trên 120 mẫu val: {accuracy*100:.2f}%\n")
print(f"{'#':<5} {'Price':>8} {'Qty':>6} {'True':<10} {'Pred':<10} {'normal':>8} {'bulking':>8} {'event':>8} {'OK?'}")
print("-" * 75)
for i in range(len(df_val)):
    p, q   = X_val_raw[i]
    true   = y_true[i]
    pred   = pred_labels[i]
    ok     = "✓" if pred == true else "✗"
    n, b, e = probs[i]
    print(f"{i+1:<5} {p:>8.1f} {q:>6.0f} {true:<10} {pred:<10} {n*100:>7.1f}% {b*100:>7.1f}% {e*100:>7.1f}% {ok}")

# ==========================================================
# 4. XUẤT RA CSV ĐỂ PHÂN TÍCH
# ==========================================================
df_result = pd.DataFrame({
    'price'      : X_val_raw[:, 0],
    'quantity'   : X_val_raw[:, 1],
    'true_label' : y_true,
    'pred_label' : pred_labels,
    'prob_normal' : probs[:, 0],
    'prob_bulking': probs[:, 1],
    'prob_event'  : probs[:, 2],
    'correct'    : [p == t for p, t in zip(pred_labels, y_true)]
})
df_result.to_csv('predict_results.csv', index=False)
print(f"\nĐã lưu kết quả vào 'predict_results.csv'.")