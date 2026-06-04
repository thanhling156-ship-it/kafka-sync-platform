import numpy as np

def train_model(X_train_raw, y_train, X_val_raw, y_val,
                learning_rate=0.01, epochs=5001, lambda_reg=0.001):
    np.random.seed(42)
    N   = X_train_raw.shape[0]
    N_v = X_val_raw.shape[0]

    # Thêm cột bias
    X_train = np.hstack([X_train_raw, np.ones((N,   1))])
    X_val   = np.hstack([X_val_raw,   np.ones((N_v, 1))])

    input_dim  = X_train.shape[1]
    size_label = y_train.shape[1]

    W1 = np.random.randn(input_dim, 16) * np.sqrt(2.0 / input_dim)
    W2 = np.random.randn(17, 16)        * np.sqrt(2.0 / 17)
    W3 = np.random.randn(16, size_label)* np.sqrt(2.0 / 16)

    def forward(X_in):
        Z1     = X_in @ W1
        H_relu = np.maximum(0, Z1)
        H      = np.hstack([H_relu, np.ones((X_in.shape[0], 1))])
        Z2     = H @ W2
        Q      = np.maximum(0, Z2)
        L      = Q @ W3
        shift  = L - np.max(L, axis=1, keepdims=True)
        A3     = np.exp(shift) / np.sum(np.exp(shift), axis=1, keepdims=True)
        return Z1, H_relu, H, Z2, Q, A3

    eps = 1e-15
    best_val_loss = np.inf
    best_weights  = None

    for i in range(epochs):
        # ── Forward train ────────────────────────────────────────────────────
        Z1, H_relu, H, Z2, Q, A3 = forward(X_train)

        E = -np.sum(y_train * np.log(np.clip(A3, eps, 1 - eps))) / N
        E += (lambda_reg / (2 * N)) * (np.sum(W1**2) + np.sum(W2**2) + np.sum(W3**2))

        # ── Validation loss ──────────────────────────────────────────────────
        _, _, _, _, _, A3_val = forward(X_val)
        E_val = -np.sum(y_val * np.log(np.clip(A3_val, eps, 1 - eps))) / N_v

        if E_val < best_val_loss:
            best_val_loss = E_val
            best_weights  = (W1.copy(), W2.copy(), W3.copy())

        if i % 100 == 0:
            print(f"Iter {i:5d} | Train Loss: {E:.4f} | Val Loss: {E_val:.4f}")

        # ── Backward ─────────────────────────────────────────────────────────
        dE_dL  = (A3 - y_train) / N
        dE_dQ  = dE_dL @ W3.T
        dE_dZ2 = dE_dQ * (Z2 > 0)
        dE_dH  = dE_dZ2 @ W2[:16, :].T
        dE_dZ1 = dE_dH  * (Z1 > 0)

        dW1 = X_train.T @ dE_dZ1 + (lambda_reg / N) * W1
        dW2 = H.T       @ dE_dZ2 + (lambda_reg / N) * W2
        dW3 = Q.T       @ dE_dL  + (lambda_reg / N) * W3

        W1 -= learning_rate * dW1
        W2 -= learning_rate * dW2
        W3 -= learning_rate * dW3

    print(f"\n✓ Best Val Loss: {best_val_loss:.4f} — trả về weights tốt nhất")
    return best_weights