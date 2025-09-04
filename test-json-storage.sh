#!/bin/bash

# 测试 JSON 存储功能的脚本

echo "=== 测试 JSON 存储功能 ==="

# 设置基础 URL
BASE_URL="http://localhost:8080/api"

echo "1. 测试获取所有 medianMap 数据（初始状态）"
curl -X GET "$BASE_URL/medianData" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n\n"

echo "2. 模拟发送 20 批 BLE 数据到 /addData 接口"
echo "注意：这需要发送 20 次请求，每次 count 递增，最后一次 count=20 时会触发 medianMap 计算和保存"

# 创建测试数据
for i in $(seq 1 20); do
  echo "发送第 $i 批数据..."
  
  # 创建测试的 BLE 数据
  cat > /tmp/ble_data_$i.json << EOF
{
  "dto": [
    {
      "uuid": "12345678-1234-1234-1234-123456789abc",
      "major": 1,
      "minor": 100,
      "rssi": -45
    },
    {
      "uuid": "12345678-1234-1234-1234-123456789def",
      "major": 1,
      "minor": 101,
      "rssi": -50
    },
    {
      "uuid": "12345678-1234-1234-1234-123456789ghi",
      "major": 1,
      "minor": 102,
      "rssi": -55
    }
  ],
  "count": $i,
  "regional": "test_region"
}
EOF

  # 发送请求
  curl -X POST "$BASE_URL/addData" \
    -H "Content-Type: application/json" \
    -d @/tmp/ble_data_$i.json \
    -w "\nHTTP Status: %{http_code}\n" \
    -s > /dev/null
  
  echo "第 $i 批数据发送完成"
done

echo ""
echo "3. 检查是否保存了 medianMap 数据"
curl -X GET "$BASE_URL/medianData" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n\n"

echo "4. 获取特定 key 的数据（test_region_20）"
curl -X GET "$BASE_URL/medianData/test_region_20" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n\n"

echo "5. 清理测试数据"
rm -f /tmp/ble_data_*.json

echo "=== 测试完成 ==="
echo ""
echo "如果看到 medianMap 数据被成功保存，说明功能正常工作。"
echo "数据会保存在项目根目录下的 data/median_data.json 文件中。"
