package com.gyq.ble.service;

import com.gyq.ble.model.PredictResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * KNN服务，实现基于K近邻的区域预测
 */
@Slf4j
@Service
public class KnnService {
    
    /**
     * K值
     */
    private static final int K = 5;
    
    @Autowired
    private DatasetService datasetService;
    
    /**
     * 预测区域
     */
    public PredictResponse predict(List<Map<String, Object>> beacons) {
        try {
            // 构造输入向量
            double[] inputVector = constructInputVector(beacons);
            
            // 获取训练数据
            List<double[]> X = datasetService.getX();
            List<Integer> y = datasetService.getY();
            
            if (X.isEmpty() || y.isEmpty()) {
                log.warn("训练数据为空，无法进行预测");
                PredictResponse response = new PredictResponse();
                response.setRegionTop1(null);
                response.setRegionTop3(new ArrayList<>());
                return response;
            }
            
            // 检查向量长度一致性
            int expectedLength = datasetService.getBeaconColumnCount();
            if (inputVector.length != expectedLength) {
                log.error("输入向量长度({})与期望长度({})不匹配", inputVector.length, expectedLength);
                PredictResponse response = new PredictResponse();
                response.setRegionTop1(null);
                response.setRegionTop3(new ArrayList<>());
                return response;
            }
            
            // 验证训练数据向量长度
            for (int i = 0; i < X.size(); i++) {
                if (X.get(i).length != expectedLength) {
                    log.error("训练数据向量[{}]长度({})与期望长度({})不匹配", i, X.get(i).length, expectedLength);
                    PredictResponse response = new PredictResponse();
                    response.setRegionTop1(null);
                    response.setRegionTop3(new ArrayList<>());
                    return response;
                }
            }
            
            log.info("开始KNN预测，输入向量长度: {}, 训练数据样本数: {}, 期望向量长度: {}", 
                    inputVector.length, X.size(), expectedLength);
            
            // 计算距离并获取Top-K邻居
            List<Neighbor> neighbors = topK(X, y, inputVector, K);
            
            if (neighbors.isEmpty()) {
                log.warn("没有找到邻居，无法进行预测");
                PredictResponse response = new PredictResponse();
                response.setRegionTop1(null);
                response.setRegionTop3(new ArrayList<>());
                return response;
            }
            
            // Top-1预测（多数投票）
            Integer regionTop1 = voteRegion(neighbors);
            
            // Top-3预测（加权分数）
            List<PredictResponse.RegionScore> regionTop3 = top3WithScore(neighbors);
            
            log.info("KNN预测完成，Top-1: {}, Top-3数量: {}", regionTop1, regionTop3);
            
            return new PredictResponse(regionTop1, regionTop3);
            
        } catch (Exception e) {
            log.error("预测过程中发生错误", e);
            PredictResponse response = new PredictResponse();
            response.setRegionTop1(null);
            response.setRegionTop3(new ArrayList<>());
            return response;
        }
    }
    
    /**
     * 构造输入向量
     */
    private double[] constructInputVector(List<Map<String, Object>> beacons) {
        List<String> beaconColumns = datasetService.getBeaconColumns();
        double[] inputVector = new double[beaconColumns.size()];
        Arrays.fill(inputVector, -100.0); // 默认填充值
        
        // 将beacons转换为Map，方便查找
        Map<String, Double> beaconMap = new HashMap<>();
        for (Map<String, Object> beacon : beacons) {
            String uuid = (String) beacon.get("uuid");
            Integer major = (Integer) beacon.get("major");
            Integer minor = (Integer) beacon.get("minor");
            Double rssi = ((Number) beacon.get("rssi")).doubleValue();
            
            if (uuid != null && major != null && minor != null && rssi != null) {
                String key = datasetService.keyOf(uuid, major, minor);
                beaconMap.put(key, rssi);
            }
        }
        
        // 按beaconColumns的顺序填充向量
        for (int i = 0; i < beaconColumns.size(); i++) {
            String key = beaconColumns.get(i);
            inputVector[i] = beaconMap.getOrDefault(key, -100.0);
        }
        
        return inputVector;
    }
    
    /**
     * 计算欧氏距离
     */
    private double distance(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量长度不一致");
        }
        
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        
        return Math.sqrt(sum);
    }
    
    /**
     * 获取Top-K邻居
     */
    private List<Neighbor> topK(List<double[]> X, List<Integer> y, double[] input, int k) {
        List<Neighbor> neighbors = new ArrayList<>();
        
        for (int i = 0; i < X.size(); i++) {
            double dist = distance(input, X.get(i));
            neighbors.add(new Neighbor(dist, y.get(i)));
        }
        
        // 按距离升序排序，取前k个
        return neighbors.stream()
                .sorted(Comparator.comparingDouble(Neighbor::getDistance))
                .limit(k)
                .collect(Collectors.toList());
    }
    
    /**
     * Top-1区域投票
     */
    private Integer voteRegion(List<Neighbor> neighbors) {
        if (neighbors.isEmpty()) {
            return null;
        }
        
        // 统计每个区域的出现次数
        Map<Integer, Long> regionCounts = neighbors.stream()
                .collect(Collectors.groupingBy(Neighbor::getRegionId, Collectors.counting()));
        
        // 返回出现次数最多的区域
        return regionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Top-3加权分数
     */
    private List<PredictResponse.RegionScore> top3WithScore(List<Neighbor> neighbors) {
        if (neighbors.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 计算每个区域的加权分数（1/距离）
        Map<Integer, Double> regionScores = new HashMap<>();
        for (Neighbor neighbor : neighbors) {
            double weight = 1.0 / (neighbor.getDistance() + 1e-6); // 避免除零
            regionScores.merge(neighbor.getRegionId(), weight, Double::sum);
        }
        
        // 按分数降序排序，取前3个
        return regionScores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(3)
                .map(entry -> {
                    PredictResponse.RegionScore score = new PredictResponse.RegionScore();
                    score.setRegionId(entry.getKey());
                    score.setScore(entry.getValue());
                    return score;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 邻居类
     */
    private static class Neighbor {
        private final double distance;
        private final int regionId;
        
        public Neighbor(double distance, int regionId) {
            this.distance = distance;
            this.regionId = regionId;
        }
        
        public double getDistance() {
            return distance;
        }
        
        public int getRegionId() {
            return regionId;
        }
    }
}
