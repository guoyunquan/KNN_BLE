package com.gyq.ble.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON 存储服务，用于保存和读取 medianMap 数据
 */
@Slf4j
@Service
public class JsonStorageService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String dataDir = "data";
    private final String fileName = "median_data.json";
    
    /**
     * 保存 medianMap 数据到 JSON 文件
     * 
     * @param key 存储的键，格式为 "regional_count"
     * @param medianMap 要保存的中位数数据
     */
    public void saveMedianData(String key, Map<String, Double> medianMap) {
        try {
            // 确保数据目录存在
            Path dataPath = Paths.get(dataDir);
            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataPath);
                log.info("创建数据目录: {}", dataPath.toAbsolutePath());
            }
            
            // 读取现有数据
            Map<String, Map<String, Double>> allData = loadAllData();
            
            // 更新数据
            allData.put(key, medianMap);
            
            // 保存到文件
            File file = new File(dataDir, fileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, allData);
            
            log.info("成功保存 medianMap 数据，key: {}, 数据量: {}", key, medianMap.size());
            
        } catch (IOException e) {
            log.error("保存 medianMap 数据失败，key: {}", key, e);
        }
    }
    
    /**
     * 读取所有 medianMap 数据
     * 
     * @return 所有数据的映射
     */
    public Map<String, Map<String, Double>> loadAllData() {
        try {
            File file = new File(dataDir, fileName);
            if (!file.exists()) {
                log.info("JSON 文件不存在，返回空数据");
                return new HashMap<>();
            }
            
            return objectMapper.readValue(file, new TypeReference<Map<String, Map<String, Double>>>() {});
                
        } catch (IOException e) {
            log.error("读取 medianMap 数据失败", e);
            return new HashMap<>();
        }
    }
    
    /**
     * 根据 key 读取特定的 medianMap 数据
     * 
     * @param key 要读取的键
     * @return 对应的中位数数据，如果不存在则返回空 Map
     */
    public Map<String, Double> loadMedianData(String key) {
        Map<String, Map<String, Double>> allData = loadAllData();
        return allData.getOrDefault(key, new HashMap<>());
    }
    
    /**
     * 删除指定 key 的数据
     * 
     * @param key 要删除的键
     */
    public void deleteMedianData(String key) {
        try {
            Map<String, Map<String, Double>> allData = loadAllData();
            allData.remove(key);
            
            File file = new File(dataDir, fileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, allData);
            
            log.info("成功删除 medianMap 数据，key: {}", key);
            
        } catch (IOException e) {
            log.error("删除 medianMap 数据失败，key: {}", key, e);
        }
    }
    
    /**
     * 获取所有可用的 key
     * 
     * @return 所有 key 的集合
     */
    public java.util.Set<String> getAllKeys() {
        return loadAllData().keySet();
    }
}
