# BLE Beacon KNN Indoor Localization Backend

基于BLE信标的KNN室内定位后端系统，实现区域分类功能。

## 技术栈

- **Java 17**
- **Spring Boot 3.3.x**
- **Maven**
- **OpenCSV** - CSV文件读写
- **Lombok** - 代码简化

## 项目结构

```
src/main/java/com/gyq/ble/
├─ BleLocatorApplication.java    # 主应用类
├─ controller/
│   └─ ApiController.java        # API控制器
├─ model/                        # 数据模型
│   ├─ BeaconReading.java        # 信标读取数据
│   ├─ CollectPayload.java       # 采集请求载荷
│   ├─ PredictPayload.java       # 预测请求载荷
│   ├─ CollectResponse.java      # 采集响应
│   ├─ PredictResponse.java      # 预测响应
│   ├─ ReloadResponse.java       # 重载响应
│   └─ HealthResponse.java       # 健康检查响应
└─ service/                      # 业务服务
    ├─ DatasetService.java       # 数据集服务
    └─ KnnService.java          # KNN算法服务
```

## 快速开始

### 1. 启动应用

```bash
# 进入项目目录
cd /Users/guoyunquan/IdeaProjects/ble

# 启动应用
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

### 2. API接口

#### 采集样本

```bash
POST /api/collect
Content-Type: application/json

{
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
}
```

响应：
```json
{
  "ok": true,
  "saved": 1,
  "beaconCols": 25
}
```

#### 区域预测

```bash
POST /api/predict
Content-Type: application/json

{
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
}
```

响应：
```json
{
  "regionTop1": 12,
  "regionTop3": [
    {
      "regionId": 12,
      "score": 0.72
    },
    {
      "regionId": 11,
      "score": 0.17
    },
    {
      "regionId": 13,
      "score": 0.11
    }
  ]
}
```

#### 热加载数据集

```bash
GET /api/reload
```

响应：
```json
{
  "ok": true,
  "beaconCols": 25,
  "samples": 642
}
```

#### 健康检查

```bash
GET /api/health
```

响应：
```json
{
  "status": "UP",
  "beaconCols": 25,
  "samples": 642
}
```

## 核心功能

### 1. 数据集管理

- 自动创建/加载 `dataset.csv` 文件
- 动态扩展信标列
- 线程安全的数据读写
- 缺失值填充（-100）

### 2. KNN算法

- K=5近邻
- 欧氏距离计算
- Top-1多数投票
- Top-3加权分数（1/距离）

### 3. 实时预测

- 基于滑窗稳定化的RSSI数据
- 支持动态信标扩展
- 实时区域分类

## 数据格式

### CSV文件结构

```
region_id,device,time_slot,heading,uuid1-major1-minor1,uuid2-major2-minor2,...
12,MI11,AM,E,-62,-76,...
13,MI11,AM,E,-65,-78,...
```

### 信标键名格式

```
{uuid}-{major}-{minor}
```

例如：`FDA50693-A4E2-4FB1-AFCF-C6EB07647825-10835-355`

## 配置说明

- **端口**: 8080
- **数据集文件**: dataset.csv
- **RSSI缺失值**: -100.0
- **KNN K值**: 5

## 注意事项

1. 首次启动会自动创建 `dataset.csv` 文件
2. 信标列会根据采集数据动态扩展
3. 支持热加载，无需重启即可更新模型
4. 所有API都支持CORS跨域请求

## 故障排除

### 常见问题

1. **端口占用**: 修改 `application.yml` 中的 `server.port`
2. **文件权限**: 确保应用有读写 `dataset.csv` 的权限
3. **内存不足**: 大数据集可能需要调整JVM内存参数

### 日志查看

应用启动后会在控制台输出详细日志，包括：
- 数据集加载状态
- API调用记录
- 错误信息

## 开发说明

### 添加新功能

1. 在 `model` 包中添加新的数据模型
2. 在 `service` 包中实现业务逻辑
3. 在 `controller` 包中暴露API接口

### 测试

```bash
# 运行测试
mvn test

# 集成测试
mvn spring-boot:run
# 然后使用curl或Postman测试API
```

## 许可证

MIT License