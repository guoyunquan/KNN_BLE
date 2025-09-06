package com.gyq.ble.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
import java.util.stream.Collectors;

/**
 * API控制器，提供BLE定位相关的REST接口
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {
    private final static ConcurrentHashMap<Integer, List<BeaconReading>> map = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<String,Integer> countMap = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<String,Integer> nameMap = new ConcurrentHashMap<>();

    
    @Autowired
    private JsonStorageService jsonStorageService;
    

    
    /**
     * 区域预测
     * 
     * POST /api/predict
     */
    @PostMapping("/predict")
    public ResponseEntity<PredictResponse> predict(@RequestBody PredictPayload payload) {
        HashMap<Object, Object> hashMap = new HashMap<>();
        HashMap<Object, Object> result = new HashMap<>();
        //预处理数据
        // 1) 过滤 RSSI：(-85, 0) 区间
        List<BeaconReading> list = payload.getBeacons();
        List<BeaconReading> readings = list.stream()
                .filter(item -> item.getRssi() < 0 && item.getRssi() > -85)
                .toList();
        readings.forEach(item -> {
            String key = buildKey(item);
            hashMap.put(key, item.getRssi());
        });
     jsonStorageService.loadAllData().forEach((key, map) -> {
         String jsonString = JSON.toJSONString(map);
         String jsonString1 = JSON.toJSONString(hashMap);
         Double v = SimilarityMetricsDemo.cosDouble(jsonString1, jsonString);
         System.out.println(key + ":" + v);
         
         // 只有当相似度不为 null 时才添加到结果中
         if (v != null) {
             result.put(key, v);
         } else {
             System.out.println("警告: " + key + " 的相似度计算结果为 null，跳过此结果");
         }
     });


        // 转换为 List
        List<Map.Entry<Object, Object>> listA = new ArrayList<>(result.entrySet());

        // 排序（从大到小）
        listA.sort(new Comparator<Map.Entry<Object, Object>>() {
            @Override
            public int compare(Map.Entry<Object, Object> o1, Map.Entry<Object, Object> o2) {
                Double v1 = (Double) o1.getValue();
                Double v2 = (Double) o2.getValue();

                // 处理 null 值：null 值排在最后
                if (v1 == null && v2 == null) return 0;
                if (v1 == null) return 1;  // v1 为 null，排在后面
                if (v2 == null) return -1; // v2 为 null，v1 排在前面

                return v2.compareTo(v1); // 从大到小
            }
        });

        // 打印排序结果
        System.out.println("排序后：");
        for (Map.Entry<Object, Object> entry : listA) {
            Double value = (Double) entry.getValue();
            if (value != null) {
                System.out.printf("%s => %.2f%n", entry.getKey(), value);
            } else {
                System.out.printf("%s => null%n", entry.getKey());
            }
        }

        // 如果需要一个保持顺序的 Map
        Map<Object, Object> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : listA) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        System.out.println("\n有序 Map: " + sortedMap);

        // 实现 KNN 相似度匹配
        PredictResponse response = performKnnPrediction(sortedMap);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 执行 KNN 相似度匹配预测
     * 
     * @param sortedMap 按相似度排序的结果映射
     * @return 预测响应
     */
    private PredictResponse performKnnPrediction(Map<Object, Object> sortedMap) {
        PredictResponse response = new PredictResponse();
        
        if (sortedMap.isEmpty()) {
            log.warn("没有找到任何相似度数据，无法进行预测");
            return response;
        }
        
        // 提取区域名称和相似度
        Map<String, Double> regionSimilarities = new HashMap<>();
        
        for (Map.Entry<Object, Object> entry : sortedMap.entrySet()) {
            String key = (String) entry.getKey();
            Double similarity = (Double) entry.getValue();
            
            if (similarity != null) {
                // 从点位名称中提取区域名称（如 "1_3" -> "1"）
                String regionName = extractRegionName(key);
                if (regionName != null) {
                    // 如果同一区域有多个点位，取最高相似度
                    regionSimilarities.merge(regionName, similarity, Double::max);
                }
            }
        }
        
        if (regionSimilarities.isEmpty()) {
            log.warn("没有找到有效的区域相似度数据");
            return response;
        }
        
        // 按相似度排序区域
        List<Map.Entry<String, Double>> regionList = new ArrayList<>(regionSimilarities.entrySet());
        regionList.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue())); // 从高到低排序
        
        // 设置 Top-1 预测结果
        if (!regionList.isEmpty()) {
            String topRegion = regionList.get(0).getKey();
            try {
                response.setRegionTop1(Integer.parseInt(topRegion));
                log.info("Top-1 预测区域: {}, 相似度: {}", topRegion, regionList.get(0).getValue());
            } catch (NumberFormatException e) {
                log.warn("区域名称无法转换为整数: {}", topRegion);
            }
        }
        
        // 设置 Top-3 预测结果
        List<PredictResponse.RegionScore> top3List = new ArrayList<>();
        int count = Math.min(3, regionList.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Double> entry = regionList.get(i);
            try {
                Integer regionId = Integer.parseInt(entry.getKey());
                Double score = entry.getValue();
                top3List.add(new PredictResponse.RegionScore(regionId, score));
            } catch (NumberFormatException e) {
                log.warn("区域名称无法转换为整数: {}", entry.getKey());
            }
        }
        response.setRegionTop3(top3List);
        
        log.info("KNN 预测完成，Top-1: {}, Top-3: {}", 
                response.getRegionTop1(), 
                top3List.stream().map(rs -> rs.getRegionId() + "(" + String.format("%.3f", rs.getScore()) + ")").collect(Collectors.joining(", ")));
        
        return response;
    }
    
    /**
     * 从点位名称中提取区域名称
     * 例如: "1_3" -> "1", "2_5" -> "2"
     * 
     * @param pointName 点位名称
     * @return 区域名称，如果格式不正确则返回 null
     */
    private String extractRegionName(String pointName) {
        if (pointName == null || pointName.isEmpty()) {
            return null;
        }
        
        int underscoreIndex = pointName.indexOf('_');
        if (underscoreIndex > 0) {
            return pointName.substring(0, underscoreIndex);
        }
        
        // 如果没有下划线，返回整个字符串
        return pointName;
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

            nameMap.put(bleDto.getRegional(),nameMap.getOrDefault(bleDto.getRegional(),0)+1);
            log.info("{}号区域，第{}次",bleDto.getRegional(),nameMap.get(bleDto.getRegional()));
            // 3.6 保存 medianMap 数据到本地 JSON 文件
            String storageKey = bleDto.getRegional() + "_" + nameMap.get(bleDto.getRegional());
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






        public static void main(String[] args) {
            String value = "{\n" +
                    "    \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1012_10835\" : -63.0,\n" +
                    "    \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1063_10835\" : -68.0,\n" +
                    "    \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1100_10835\" : -73.0,\n" +
                    "    \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1045_10835\" : -62.0,\n" +
                    "    \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1094_10835\" : -60.5,\n" +
                    "    \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1050_10835\" : -67.5,\n" +
                    "    \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1040_10835\" : -68.0\n" +
                    "  }";

            String value1 = "{\n" +
                    "     \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1063_10835\" : -67.0,\n" +
                    "     \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1012_10835\" : -68.0,\n" +
                    "     \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1100_10835\" : -70.0,\n" +
                    "     \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1045_10835\" : -74.0,\n" +
                    "     \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1094_10835\" : -78.0,\n" +
                    "     \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1050_10835\" : -68.0,\n" +
                    "     \"FDA50693-A4E2-4FB1-AFCF-C6EB07647825_1040_10835\" : -56.0\n" +
                    "   }";

            // 1) 解析为 Map
            Map<String, Double> mapA = parseToMap(value);
            Map<String, Double> mapB = parseToMap(value1);

            // 2) 取共同 key，并排序，保证两边一致对齐
            List<String> commonKeysSorted = getCommonKeysSorted(mapA, mapB);

            // 3) 构造向量
            double[] vecA = toVector(mapA, commonKeysSorted);
            double[] vecB = toVector(mapB, commonKeysSorted);

            // 4) 计算余弦相似度
            double similarity = cosine(vecA, vecB);

            // 输出
            System.out.println("共同ID数量: " + commonKeysSorted.size());
            System.out.println("共同ID(排序后): " + commonKeysSorted);
            System.out.println("基于共同ID的余弦相似度为: " + similarity);

            // 额外：打印对齐后的值，便于人工核验
            System.out.println("\n对齐后的 (key, A, B)：");
            for (String k : commonKeysSorted) {
                System.out.printf("%s => A: %s, B: %s%n", k, mapA.get(k), mapB.get(k));
            }
        }

        /** 将 JSON 字符串解析为 Map<String, Double>（仅保留数值项） */
        public static Map<String, Double> parseToMap(String jsonStr) {
            JSONObject obj = JSON.parseObject(jsonStr);
            Map<String, Double> map = new HashMap<>();
            for (Map.Entry<String, Object> e : obj.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Number) {
                    map.put(e.getKey(), ((Number) v).doubleValue());
                } else if (v instanceof String) {
                    // 兜底：尝试把字符串转 double
                    try {
                        map.put(e.getKey(), Double.parseDouble((String) v));
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
            return map;
        }

        /** 求共同 key，并进行字典序排序，保证向量对齐的一致性 */
        public static List<String> getCommonKeysSorted(Map<String, Double> a, Map<String, Double> b) {
            return a.keySet().stream()
                    .filter(b::containsKey)
                    .sorted() // 关键：排序！
                    .collect(Collectors.toList());
        }

        /** 按给定顺序将 Map 映射为向量 */
        public static double[] toVector(Map<String, Double> map, List<String> orderedKeys) {
            double[] v = new double[orderedKeys.size()];
            for (int i = 0; i < orderedKeys.size(); i++) {
                String k = orderedKeys.get(i);
                // 这里用交集后理论上都存在；为了健壮性，缺失则按 0 处理
                v[i] = map.getOrDefault(k, 0.0);
            }
            return v;
        }

        /** 余弦相似度：[-1, 1] */
        public static double cosine(double[] a, double[] b) {
            if (a == null || b == null) throw new IllegalArgumentException("向量不能为 null");
            if (a.length != b.length) throw new IllegalArgumentException("向量长度必须相同");
            double dot = 0.0, na = 0.0, nb = 0.0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                na += a[i] * a[i];
                nb += b[i] * b[i];
            }
            if (na == 0 || nb == 0) return 0.0;
            return dot / (Math.sqrt(na) * Math.sqrt(nb));
        }







    }

