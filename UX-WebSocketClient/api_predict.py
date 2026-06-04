import numpy as np
from flask import Flask, jsonify, request

app = Flask(__name__)

try:
    saved_data = np.load("weights.npz")
    W1    = saved_data["W1"]
    W2    = saved_data["W2"]
    W3    = saved_data["W3"]
    X_min = saved_data["X_min"]
    X_max = saved_data["X_max"]
    range_val = np.where(X_max - X_min == 0, 1, X_max - X_min)  # ← nhất quán với training
    print("--> Đã nạp thành công bộ trọng số.")
except Exception as e:
    print(f"/!\\ Lỗi không thể nạp file weights.npz: {str(e)}")

labels = ["normal", "bulking", "event"]

@app.route("/api/predict", methods=["POST"])
def predict_endpoint():
    try:
        data = request.get_json()
        price    = data.get("price")
        quantity = data.get("quantity")

        # Validate tham số đầu vào
        if price is None or quantity is None:
            return jsonify({"status": "error", "message": "Thiếu tham số price hoặc quantity"}), 400

        price    = float(price)
        quantity = float(quantity)

        # 1. Chuẩn hóa
        X_raw    = np.array([[price, quantity]])
        X_scaled = (X_raw - X_min) / range_val

        # 2. Forward pass
        X      = np.hstack([X_scaled, np.ones((1, 1))])
        Z1     = X @ W1
        H      = np.hstack([np.maximum(0, Z1), np.ones((1, 1))])
        Z2     = H @ W2
        Q      = np.maximum(0, Z2)
        L      = Q @ W3
        shift  = L - np.max(L, axis=1, keepdims=True)
        probs  = np.exp(shift) / np.sum(np.exp(shift), axis=1, keepdims=True)

        pred_idx    = np.argmax(probs[0])
        final_label = labels[pred_idx]
        confidence  = float(probs[0][pred_idx])

        return jsonify({
            "status"     : "success",
            "result"     : final_label,
            "probability": round(confidence, 4),
            "all_probs"  : {          # ← thêm: trả về cả 3 xác suất tiện debug
                "normal" : round(float(probs[0][0]), 4),
                "bulking": round(float(probs[0][1]), 4),
                "event"  : round(float(probs[0][2]), 4),
            }
        })

    except ValueError:
        return jsonify({"status": "error", "message": "price và quantity phải là số"}), 400
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)