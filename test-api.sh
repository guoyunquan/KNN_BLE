#!/bin/bash

# BLE定位后端API测试脚本
# 使用方法: ./test-api.sh

BASE_URL="http://localhost:8080"

echo "=== BLE定位后端API测试 ==="
echo ""

# 1. 健康检查
echo "1. 健康检查"
curl -s "$BASE_URL/api/health" | jq '.' 2>/dev/null || curl -s "$BASE_URL/api/health"
echo ""

# 2. 采集样本
echo "2. 采集样本"
curl -s -X POST "$BASE_URL/api/collect" \
  -H "Content-Type: application/json" \
  -d '{
    "region_id": 12,
    "device": "MI11",
    "time_slot": "AM",
    "heading": "E",
    "beacons": [
      {
        "uuid": "FDA50693-A4E2-4FB1-AFCF-C6EB07647825",
        "major": 10835,
        "minor": 355,
        "rssi": -62
      },
      {
        "uuid": "FDA50693-A4E2-4FB1-AFCF-C6EB07647825",
        "major": 10835,
        "minor": 358,
        "rssi": -76
      }
    ]
  }' | jq '.' 2>/dev/null || curl -s -X POST "$BASE_URL/api/collect" \
  -H "Content-Type: application/json" \
  -d '{"region_id": 12, "device": "MI11", "time_slot": "AM", "heading": "E", "beacons": [{"uuid":"FDA50693-A4E2-4FB1-AFCF-C6EB07647825","major":10835,"minor":355,"rssi":-62},{"uuid":"FDA50693-A4E2-4FB1-AFCF-C6EB07647825","major":10835,"minor":358,"rssi":-76}]}'
echo ""

# 3. 区域预测
echo "3. 区域预测"
curl -s -X POST "$BASE_URL/api/predict" \
  -H "Content-Type: application/json" \
  -d '{
    "beacons": [
      {
        "uuid": "FDA50693-A4E2-4FB1-AFCF-C6EB07647825",
        "major": 10835,
        "minor": 355,
        "rssi": -61
      },
      {
        "uuid": "FDA50693-A4E2-4FB1-AFCF-C6EB07647825",
        "major": 10835,
        "minor": 358,
        "rssi": -77
      }
    ]
  }' | jq '.' 2>/dev/null || curl -s -X POST "$BASE_URL/api/predict" \
  -H "Content-Type: application/json" \
  -d '{"beacons": [{"uuid":"FDA50693-A4E2-4FB1-AFCF-C6EB07647825","major":10835,"minor":355,"rssi":-61},{"uuid":"FDA50693-A4E2-4FB1-AFCF-C6EB07647825","major":10835,"minor":358,"rssi":-77}]}'
echo ""

# 4. 重载数据集
echo "4. 重载数据集"
curl -s "$BASE_URL/api/reload" | jq '.' 2>/dev/null || curl -s "$BASE_URL/api/reload"
echo ""

echo "=== 测试完成 ==="
echo ""
echo "注意：如果看到错误，请确保后端服务已经启动 (mvn spring-boot:run)"
