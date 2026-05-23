import asyncio
import websockets

async def listen(user_id):
    uri = f"ws://localhost:8090/ws/orders/{user_id}"
    async with websockets.connect(uri) as ws:
        print(f"Connected, đang chờ thông báo cho user {user_id}...")
        while True:
            message = await ws.recv()
            print(f"Thông báo: {message}")

user_id = input("Nhập userId: ")
asyncio.run(listen(user_id))