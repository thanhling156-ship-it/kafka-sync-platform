import asyncio
import aiohttp
import websockets
import time
import statistics

# Cấu hình hệ thống
HTTP_URL = "http://localhost:8083/api/v1/orders"
WS_URL_TEMPLATE = "ws://localhost:8083/ws/orders/{}"

# Thống kê toàn cục
stats = {
    "http_sent": 0,
    "http_success": 0,
    "http_fail": 0,
    "saga_received": 0,
    "saga_latencies": []
}
stats_lock = asyncio.Lock()

async def run_single_iteration(session, worker_id, iteration_idx):
    """Chạy duy nhất 1 vòng lặp: Kết nối -> Tạo đơn -> Nhận Noti -> Thoát"""
    # Trích xuất số từ worker_id (ví dụ "vu_1" lấy ra số 1)
    worker_num = int(worker_id.split('_')[1])
    
    # Tạo số định danh duy nhất tăng dần cho từng request để tránh trùng lặp id trong database
    # Công thức này đảm bảo định dạng luôn là user-v- số_nguyên (ví dụ: user-v-1, user-v-2, user-v-51...)
    unique_num = (iteration_idx * 20) + worker_num
    user_id = f"user-v-{unique_num}"
    
    ws_url = WS_URL_TEMPLATE.format(user_id)
    
    payload = {
        "userId": user_id,
        "productId": "MACBOOK-M3",
        "quantity": 1,
        "unitPrice": 2000.0,
        "address": "123 Đường ABC, Hà Nội",
        "number": "0987654321"
    }
    
    start_time = time.time()
    try:
        async with websockets.connect(ws_url) as websocket:
            # 1. Bắn HTTP POST tạo đơn
            async with stats_lock:
                stats["http_sent"] += 1
                
            async with session.post(HTTP_URL, json=payload) as response:
                if response.status == 202:
                    async with stats_lock: stats["http_success"] += 1
                else:
                    async with stats_lock: stats["http_fail"] += 1
                    return

            # 2. Đứng đợi duy nhất thông báo Kafka Saga trả về
            async for message in websocket:
                # Kiểm tra từ khóa từ luồng Saga (bỏ qua từ khóa expired của Redis)
                if "vận chuyển" in message:
                    latency = (time.time() - start_time) * 1000
                    async with stats_lock:
                        stats["saga_received"] += 1
                        stats["saga_latencies"].append(latency)
                    break # Thoát luồng, đóng socket ngay lập tức không đợi Redis
                    
    except Exception:
        async with stats_lock: stats["http_fail"] += 1

async def virtual_user_worker(worker_id, stop_event, session):
    """Mỗi Worker đóng vai trò là 1 VU lặp đi lặp lại việc tạo đơn"""
    iteration = 0
    while not stop_event.is_set():
        await run_single_iteration(session, worker_id, iteration)
        iteration += 1
        await asyncio.sleep(0.5) # Nghỉ ngắn giữa các lượt lặp để tránh nghẽn cục bộ

async def main():
    start_test_time = time.time()
    stop_event = asyncio.Event()
    active_workers = {}
    
    RAMP_UP_DURATION = 30  # 30 giây tăng tải
    HOLD_DURATION = 60     # 60 giây giữ tải tối đa
    TOTAL_DURATION = RAMP_UP_DURATION + HOLD_DURATION

    async with aiohttp.ClientSession() as session:
        print(f"🔥 BẮT ĐẦU BÀI TEST TẢI TĂNG DẦN (Tổng thời gian: {TOTAL_DURATION}s) \n")
        
        last_log_time = time.time()
        
        while True:
            elapsed = time.time() - start_test_time
            if elapsed >= TOTAL_DURATION:
                break
                
            # Tính toán số lượng VU cần kích hoạt tại thời điểm hiện tại
            if elapsed < RAMP_UP_DURATION:
                # Tăng đều từ 10 đến 50 user trong 30 giây đầu
                target_vus = int(10 + ((20 - 10) * (elapsed / RAMP_UP_DURATION)))
            else:
                # Duy trì 50 user trong 60 giây tiếp theo
                target_vus = 20
                
            # Điều chỉnh số lượng Worker thực tế chạy song song
            current_vus = len(active_workers)
            if current_vus < target_vus:
                for i in range(current_vus, target_vus):
                    w_id = f"vu_{i+1}"
                    task = asyncio.create_task(virtual_user_worker(w_id, stop_event, session))
                    active_workers[w_id] = task
                    
            # In trạng thái tiến trình mỗi 5 giây
            if time.time() - last_log_time >= 5:
                phase = "RAMP-UP" if elapsed < RAMP_UP_DURATION else "HOLD TẢI"
                print(f"⏱️ [{phase}] Đã chạy: {int(elapsed)}s | Số lượng User ảo (VUs) hiện tại: {len(active_workers)}/{target_vus}")
                last_log_time = time.time()
                
            await asyncio.sleep(0.5)
            
        # Dừng toàn bộ hệ thống
        stop_event.set()
        print("\n🏁 Kết thúc thời gian test. Đang hủy các luồng thừa và tính toán số liệu...")
        for task in active_workers.values():
            task.cancel()
        await asyncio.gather(*active_workers.values(), return_exceptions=True)

    # --- IN BẢNG TỔNG KẾT KẾT QUẢ ---
    total_http = stats["http_sent"]
    success_http = stats["http_success"]
    fail_http = stats["http_fail"]
    saga_cnt = stats["saga_received"]
    latencies = stats["saga_latencies"]
    
    success_rate = (success_http / total_http * 100) if total_http > 0 else 0
    avg_latency = statistics.mean(latencies) if latencies else 0
    min_latency = min(latencies) if latencies else 0
    max_latency = max(latencies) if latencies else 0
    p95_latency = statistics.quantiles(latencies, n=20)[18] if len(latencies) >= 20 else avg_latency

    print("\n" + "="*50)
    print("                 BẢNG TỔNG KẾT HIỆU NĂNG                ")
    print("="*50)
    print(f" Đơn hàng đã gửi (HTTP Request)  : {total_http} đơn")
    print(f" Đơn hàng tiếp nhận thành công    : {success_http} đơn")
    print(f" Đơn hàng thất bại (Lỗi kết nối) : {fail_http} đơn")
    print(f" Tỷ lệ thành công (Success Rate) : {success_rate:.2f}%")
    print(f" Thông báo Kafka Saga nhận được  : {saga_cnt}/{success_http}")
    print("-"*50)
    print(" THỜI GIAN PHẢN HỒI LUỒNG SAGA (LATENCY):")
    print(f"  - Thấp nhất (Min)              : {min_latency:.2f} ms")
    print(f"  - Trung bình (Avg)             : {avg_latency:.2f} ms")
    print(f"  - Cao nhất (Max)               : {max_latency:.2f} ms")
    print(f"  - Tiêu chuẩn 95% (p95)         : {p95_latency:.2f} ms")
    print("="*50)

if __name__ == "__main__":
    asyncio.run(main())