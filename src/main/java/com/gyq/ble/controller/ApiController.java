package com.gyq.ble.controller;

import com.gyq.ble.model.*;
import com.gyq.ble.service.DatasetService;
import com.gyq.ble.service.JsonStorageService;
import com.gyq.ble.service.KnnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API控制器，提供BLE定位相关的REST接口
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {
    private final static ConcurrentHashMap<Integer, List<BeaconReading>> map = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<String,Integer> countMap = new ConcurrentHashMap<>();
    
    @Autowired
    private DatasetService datasetService;
    
    @Autowired
    private KnnService knnService;
    
    @Autowired
    private JsonStorageService jsonStorageService;
    
    /**
     * 采集样本
     * 
     * POST /api/collect
     */
    @PostMapping("/collect")
    public ResponseEntity<CollectResponse> collect(@RequestBody CollectPayload payload) {
        try {
            // 参数校验
            if (payload.getRegion_id() == null) {
                return ResponseEntity.badRequest()
                        .body(new CollectResponse(false, 0, 0));
            }
            
            if (payload.getBeacons() == null || payload.getBeacons().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new CollectResponse(false, 0, 0));
            }
            
            // 构造元数据
            Map<String, Object> meta = new HashMap<>();
            meta.put("region_id", payload.getRegion_id());
            meta.put("x", payload.getX());
            meta.put("y", payload.getY());
            meta.put("device", payload.getDevice());
            meta.put("time_slot", payload.getTime_slot());
            meta.put("heading", payload.getHeading());
            
            // 构造RSSI映射
            Map<String, Double> rssiByKey = new HashMap<>();
            for (BeaconReading beacon : payload.getBeacons()) {
                if (beacon.getUuid() != null && beacon.getMajor() != null && 
                    beacon.getMinor() != null && beacon.getRssi() != null) {
                    String key = datasetService.keyOf(beacon.getUuid(), beacon.getMajor(), beacon.getMinor());
                    rssiByKey.put(key, beacon.getRssi().doubleValue());
                }
            }
            
            // 保存样本
            datasetService.appendSample(meta, rssiByKey);
            
            // 返回响应
            CollectResponse response = new CollectResponse(
                    true, 
                    1, 
                    datasetService.getBeaconColumnCount()
            );
            
            log.info("样本采集成功，区域ID: {}, 信标数: {}", payload.getRegion_id(), payload.getBeacons().size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("样本采集失败", e);
            return ResponseEntity.internalServerError()
                    .body(new CollectResponse(false, 0, 0));
        }
    }
    
    /**
     * 区域预测
     * 
     * POST /api/predict
     */
    @PostMapping("/predict")
    public ResponseEntity<PredictResponse> predict(@RequestBody PredictPayload payload) {
        try {
            // 参数校验
            if (payload.getBeacons() == null || payload.getBeacons().isEmpty()) {
                return ResponseEntity.badRequest().body(new PredictResponse());
            }
            
            // 转换为Map格式
            List<Map<String, Object>> beacons = new ArrayList<>();
            for (BeaconReading beacon : payload.getBeacons()) {
                Map<String, Object> beaconMap = new HashMap<>();
                beaconMap.put("uuid", beacon.getUuid());
                beaconMap.put("major", beacon.getMajor());
                beaconMap.put("minor", beacon.getMinor());
                beaconMap.put("rssi", beacon.getRssi());
                beacons.add(beaconMap);
            }
            
            // 进行预测
            PredictResponse response = knnService.predict(beacons);
            
//            log.info("区域预测完成，Top-1: {}, Top-3的值: {}",
//                    response.getRegionTop1(),
//                    response.getRegionTop3());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("区域预测失败", e);
            return ResponseEntity.internalServerError().body(new PredictResponse());
        }
    }
    
    /**
     * 热加载数据集
     * 
     * GET /api/reload
     */
    @GetMapping("/reload")
    public ResponseEntity<ReloadResponse> reload() {
        try {
            datasetService.load();
            
            ReloadResponse response = new ReloadResponse(
                    true,
                    datasetService.getBeaconColumnCount(),
                    datasetService.getSampleCount()
            );
            
            log.info("数据集重载成功，信标列数: {}, 样本数: {}", 
                    response.getBeaconCols(), response.getSamples());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("数据集重载失败", e);
            return ResponseEntity.internalServerError()
                    .body(new ReloadResponse(false, 0, 0));
        }
    }
    
    /**
     * 健康检查
     * 
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        try {
            HealthResponse response = new HealthResponse(
                    "UP",
                    datasetService.getBeaconColumnCount(),
                    datasetService.getSampleCount()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return ResponseEntity.internalServerError()
                    .body(new HealthResponse("DOWN", 0, 0));
        }
    }


    /**
     * 新增数据
     */
    @PostMapping("/addData")
    public ResponseEntity<Void> addData(@RequestBody BleDto bleDto) {
        // 1) 过滤 RSSI：(-85, 0) 区间
        List<BeaconReading> list = bleDto.getDto();
        List<BeaconReading> readings = list.stream()
                .filter(item -> item.getRssi() < 0 && item.getRssi() > -85)
                .toList();

        // 2) 缓存当次数据
        map.put(bleDto.getCount(), readings);
        System.out.println("当前数据："+bleDto.getCount());
        // 3) 当累计到第 20 批时开始统计
        if (bleDto.getCount() == 20) {
            // 3.1 统计每个 beacon（uuid_minor_major）的出现次数
            for (Map.Entry<Integer, List<BeaconReading>> integerListEntry : map.entrySet()) {
                integerListEntry.getValue().forEach(item -> {
                    String key = buildKey(item);
                    countMap.put(key, countMap.getOrDefault(key, 0) + 1);
                });
            }

            log.info("原始的 countMap 数据是：{}", countMap);

            // 3.2 过滤出现次数 <= 5 的 beacon
            countMap.entrySet().removeIf(e -> e.getValue() <= 5);

            log.info("过滤后的 countMap 数据是：{}", countMap);

            // 3.3 针对过滤后仍存在的 key，从 map 中收集其所有 rssi，并计算中位数
            Map<String, List<Integer>> rssiBucket = new HashMap<>();
            for (Map.Entry<Integer, List<BeaconReading>> entry : map.entrySet()) {
                for (BeaconReading item : entry.getValue()) {
                    String key = buildKey(item);
                    if (countMap.containsKey(key)) { // 命中需要统计中位数的 key
                        rssiBucket.computeIfAbsent(key, k -> new ArrayList<>()).add(item.getRssi());
                    }
                }
            }

            // 3.4 计算中位数
            Map<String, Double> medianMap = new HashMap<>();
            for (Map.Entry<String, List<Integer>> e : rssiBucket.entrySet()) {
                double median = calcMedian(e.getValue());
                medianMap.put(e.getKey(), median);
            }

            // 3.5 打印结果（可替换为返回前端或写库）
            log.info("各 beacon 的 RSSI 中位数结果：{}", medianMap);

            // 3.6 保存 medianMap 数据到本地 JSON 文件
            String storageKey = bleDto.getRegional() + "_" + bleDto.getCount();
            jsonStorageService.saveMedianData(storageKey, medianMap);
            log.info("已保存 medianMap 数据到 JSON 文件，key: {}", storageKey);

            // （可选）处理完成后清理缓存，防止内存涨
             map.clear();
             countMap.clear();
        }

        return ResponseEntity.ok().build();
    }

    /** 组装唯一 key：uuid_minor_major */
    private static String buildKey(BeaconReading item) {
        return item.getUuid() + "_" + item.getMinor() + "_" + item.getMajor();
    }

    /** 计算中位数：偶数个取中间两数均值，奇数个取正中间值 */
    private static double calcMedian(List<Integer> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        Collections.sort(values);
        int n = values.size();
        if ((n & 1) == 1) {
            return values.get(n / 2);
        } else {
            return (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
        }
    }

    /**
     * 获取所有已保存的 medianMap 数据
     * 
     * GET /api/medianData
     */
    @GetMapping("/medianData")
    public ResponseEntity<Map<String, Map<String, Double>>> getAllMedianData() {
        try {
            Map<String, Map<String, Double>> allData = jsonStorageService.loadAllData();
            return ResponseEntity.ok(allData);
        } catch (Exception e) {
            log.error("获取 medianMap 数据失败", e);
            return ResponseEntity.internalServerError().body(new HashMap<>());
        }
    }

    /**
     * 根据 key 获取特定的 medianMap 数据
     * 
     * GET /api/medianData/{key}
     */
    @GetMapping("/medianData/{key}")
    public ResponseEntity<Map<String, Double>> getMedianData(@PathVariable String key) {
        try {
            Map<String, Double> data = jsonStorageService.loadMedianData(key);
            if (data.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("获取 medianMap 数据失败，key: {}", key, e);
            return ResponseEntity.internalServerError().body(new HashMap<>());
        }
    }

    /**
     * 删除指定的 medianMap 数据
     * 
     * DELETE /api/medianData/{key}
     */
    @DeleteMapping("/medianData/{key}")
    public ResponseEntity<Void> deleteMedianData(@PathVariable String key) {
        try {
            jsonStorageService.deleteMedianData(key);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("删除 medianMap 数据失败，key: {}", key, e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
