# BLE Beacon KNN Indoor Localization Backend

基于BLE信标的KNN室内定位后端系统，实现区域分类功能。

## 技术栈

- **Java 17**
- **Spring Boot 3.3.x**

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
    ├─ DatasetService.java       # 数据集服务-已经不用啦
    └─ KnnService.java          # KNN算法服务-已经不用啦
```

## 快速开始

### 1. 启动应用

```bash
# 进入项目目录
cd ble

# 启动应用
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

## API 接口文档

### 1. 区域预测接口

#### 接口描述
根据传入的蓝牙信标数据，通过余弦相似度算法计算与已存储数据的相似度，并使用KNN算法进行区域预测。

#### 请求URL
```
POST /api/predict
```

#### 请求参数
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| beacons | array | 是 | 信标读数列表 |
| beacons[].uuid | string | 是 | 信标的UUID |
| beacons[].major | integer | 是 | 信标的major值 |
| beacons[].minor | integer | 是 | 信标的minor值 |
| beacons[].rssi | integer | 是 | 信标的信号强度值 |

#### 请求示例
```json
{
  "beacons": [
    {
      "uuid": "FDA50693-A4E2-4FB1-AFCF-C6EB07647825",
      "major": 10835,
      "minor": 1012,
      "rssi": -63
    }
  ]
}
```

#### 响应参数
| 参数名 | 类型 | 说明 |
|--------|------|------|
| regionTop1 | integer | Top-1预测区域ID |
| regionTop3 | array | Top-3预测区域列表 |
| regionTop3[].regionId | integer | 区域ID |
| regionTop3[].score | number | 相似度得分 |

#### 响应示例
```json
{
  "regionTop1": 1,
  "regionTop3": [
    {
      "regionId": 1,
      "score": 0.956
    },
    {
      "regionId": 2,
      "score": 0.823
    }
  ]
}
```

#### 算法说明
1. 过滤RSSI值在(-85, 0)区间的数据
2. 使用余弦相似度计算与已存储数据的匹配度
3. 采用KNN算法进行区域预测，提取相似度最高的区域

---

### 2. 数据收集接口

#### 接口描述
收集蓝牙信标数据，当累计收集20批数据后，进行数据处理和统计分析，并将结果保存到JSON文件中。

#### 请求URL
```
POST /api/addData
```

#### 请求参数
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| count | integer | 是 | 数据批次计数 |
| regional | string | 是 | 区域标识 |
| dto | array | 是 | 信标读数列表 |
| dto[].uuid | string | 是 | 信标的UUID |
| dto[].major | integer | 是 | 信标的major值 |
| dto[].minor | integer | 是 | 信标的minor值 |
| dto[].rssi | integer | 是 | 信标的信号强度值 |

#### 请求示例
```json
{
  "count": 1,
  "regional": "区域1",
  "dto": [
    {
      "uuid": "FDA50693-A4E2-4FB1-AFCF-C6EB07647825",
      "major": 10835,
      "minor": 1012,
      "rssi": -63
    }
  ]
}
```

#### 响应示例
```http
HTTP/1.1 200 OK
```

#### 处理流程
1. 过滤RSSI值在(-85, 0)区间的数据
2. 缓存当前批次数据
3. 当累计收集到20批数据时触发处理：
   - 统计每个信标出现次数
   - 过滤出现次数≤5的信标
   - 计算剩余信标的RSSI中位数
   - 将处理结果保存到JSON文件
4. 清理缓存，准备下一轮数据收集

#### 数据处理说明
- 信标标识：uuid_major_minor
- 保留出现频率较高的信标数据（出现次数>5）
- 使用中位数算法减少异常值影响
- 结果按"区域编号_次数"格式存储