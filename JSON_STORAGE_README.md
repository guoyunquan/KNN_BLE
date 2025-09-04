# JSON 存储功能说明

## 功能概述

新增了将 `medianMap` 数据存储到本地 JSON 文件的功能。当 `addData` 接口接收到第 20 批数据时，系统会：

1. 计算所有 beacon 的 RSSI 中位数
2. 将结果保存到本地 JSON 文件中
3. 使用 `bleDto.regional_bleDto.count` 作为存储的 key

## 存储格式

### 文件位置
- 文件路径：`data/median_data.json`
- 如果 `data` 目录不存在，系统会自动创建

### JSON 结构
```json
{
  "regional_count": {
    "uuid_minor_major": median_value,
    "uuid_minor_major": median_value,
    ...
  },
  "another_regional_count": {
    "uuid_minor_major": median_value,
    ...
  }
}
```

### 示例
```json
{
  "test_region_20": {
    "12345678-1234-1234-1234-123456789abc_100_1": -45.0,
    "12345678-1234-1234-1234-123456789def_101_1": -50.0,
    "12345678-1234-1234-1234-123456789ghi_102_1": -55.0
  }
}
```

## 新增 API 接口

### 1. 获取所有 medianMap 数据
```
GET /api/medianData
```

### 2. 获取特定 key 的 medianMap 数据
```
GET /api/medianData/{key}
```

### 3. 删除特定 key 的 medianMap 数据
```
DELETE /api/medianData/{key}
```

## 测试方法

1. 启动应用：
   ```bash
   mvn spring-boot:run
   ```

2. 运行测试脚本：
   ```bash
   ./test-json-storage.sh
   ```

3. 或者手动测试：
   - 向 `/api/addData` 发送 20 批数据
   - 使用 `/api/medianData` 查看保存的数据

## 技术实现

- 使用 Jackson ObjectMapper 进行 JSON 序列化/反序列化
- 使用 TypeReference 处理复杂的泛型类型
- 自动创建数据目录
- 支持数据的增删改查操作
- 线程安全的文件操作

## 注意事项

- 数据会在第 20 批时自动保存
- 每次保存后会清理内存缓存
- JSON 文件使用格式化输出，便于阅读
- 支持多个不同 key 的数据同时存储
