package com.gyq.ble.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 数据集服务，负责CSV文件的读写和内存数据管理
 */
@Slf4j
@Service
public class DatasetService {
    
    /**
     * 数据集文件名
     */
    private static final String DATASET = "qqqq.csv";
    
    /**
     * RSSI缺失值填充
     */
    private static final double RSSI_PAD = -100.0;
    
    /**
     * 内存中的样本特征矩阵
     */
    private List<double[]> X = new ArrayList<>();
    
    /**
     * 内存中的区域标签
     */
    private List<Integer> y = new ArrayList<>();
    
    /**
     * 信标列名列表
     */
    private List<String> beaconColumns = new ArrayList<>();
    
    /**
     * 读写锁，保证线程安全
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * 启动时自动加载数据集
     */
    @PostConstruct
    public void init() {
        try {
            load();
            log.info("数据集加载完成，样本数: {}, 信标列数: {}", y.size(), beaconColumns.size());
        } catch (Exception e) {
            log.error("数据集加载失败，创建新的数据集文件", e);
            createNewDataset();
        }
    }
    
    /**
     * 创建新的数据集文件
     */
    private void createNewDataset() {
        try {
            File file = new File(DATASET);
            if (!file.exists()) {
                file.createNewFile();
            }
            
            // 写入初始表头
            List<String> headers = Arrays.asList("region_id", "device", "time_slot", "heading");
            try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
                writer.writeNext(headers.toArray(new String[0]));
            }
            
            log.info("创建新的数据集文件: {}", DATASET);
        } catch (IOException e) {
            log.error("创建数据集文件失败", e);
        }
    }
    
    /**
     * 从磁盘加载数据集到内存
     */
    public void load() throws IOException, CsvException {
        lock.writeLock().lock();
        try {
            File file = new File(DATASET);
            if (!file.exists()) {
                createNewDataset();
                return;
            }
            
            X.clear();
            y.clear();
            beaconColumns.clear();
            
            try (CSVReader reader = new CSVReader(new FileReader(file))) {
                List<String[]> rows = reader.readAll();
                if (rows.isEmpty()) {
                    return;
                }
                
                String[] headers = rows.get(0);
                // 前6列是固定列：region_id, x, y, device, time_slot, heading
                // 从第7列开始是信标列
                for (int i = 6; i < headers.length; i++) {
                    beaconColumns.add(headers[i]);
                }
                
                // 读取数据行
                for (int i = 1; i < rows.size(); i++) {
                    String[] row = rows.get(i);
                    if (row.length < 6) continue;
                    
                    // 解析区域ID
                    try {
                        int regionId = Integer.parseInt(row[0]);
                        y.add(regionId);
                    } catch (NumberFormatException e) {
                        log.warn("跳过无效的区域ID行: {}", Arrays.toString(row));
                        continue;
                    }
                    
                    // 构造RSSI向量
                    double[] rssiVector = new double[beaconColumns.size()];
                    Arrays.fill(rssiVector, RSSI_PAD);
                    
                    // 填充RSSI值
                    for (int j = 6; j < Math.min(row.length, headers.length); j++) {
                        if (j - 6 < beaconColumns.size() && !row[j].trim().isEmpty()) {
                            try {
                                rssiVector[j - 6] = Double.parseDouble(row[j]);
                            } catch (NumberFormatException e) {
                                rssiVector[j - 6] = RSSI_PAD;
                            }
                        }
                    }
                    
                    X.add(rssiVector);
                }
            }
            
            log.info("数据集加载完成，样本数: {}, 信标列数: {}", y.size(), beaconColumns.size());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 追加样本到数据集
     */
    public void appendSample(Map<String, Object> meta, Map<String, Double> rssiByKey) throws IOException {
        lock.writeLock().lock();
        try {
            // 检查是否需要扩展信标列
            Set<String> newBeaconKeys = new HashSet<>(rssiByKey.keySet());
            newBeaconKeys.removeAll(beaconColumns);
            
            if (!newBeaconKeys.isEmpty()) {
                // 扩展信标列
                beaconColumns.addAll(newBeaconKeys);
                log.info("扩展信标列: {}", newBeaconKeys);
                
                // 重新构造所有样本的RSSI向量
                for (int i = 0; i < X.size(); i++) {
                    double[] oldVector = X.get(i);
                    double[] newVector = Arrays.copyOf(oldVector, beaconColumns.size());
                    Arrays.fill(newVector, oldVector.length, newVector.length, RSSI_PAD);
                    X.set(i, newVector);
                }
                
                // 重写CSV文件
                rewriteCsvFile();
            }
            
            // 构造新样本的RSSI向量
            double[] rssiVector = new double[beaconColumns.size()];
            Arrays.fill(rssiVector, RSSI_PAD);
            
            for (int i = 0; i < beaconColumns.size(); i++) {
                String key = beaconColumns.get(i);
                rssiVector[i] = rssiByKey.getOrDefault(key, RSSI_PAD);
            }
            
            // 添加到内存
            X.add(rssiVector);
            y.add((Integer) meta.get("region_id"));
            
            // 追加到CSV文件
            appendToCsv(meta, rssiVector);
            
            log.info("样本追加成功，当前样本数: {}, 信标列数: {}", y.size(), beaconColumns.size());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 重写CSV文件
     */
    private void rewriteCsvFile() throws IOException {
        File file = new File(DATASET);
        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            // 写入表头
            List<String> headers = new ArrayList<>();
            headers.add("region_id");
            headers.add("x");
            headers.add("y");
            headers.add("device");
            headers.add("time_slot");
            headers.add("heading");
            headers.addAll(beaconColumns);
            writer.writeNext(headers.toArray(new String[0]));
            
            // 写入数据行
            for (int i = 0; i < X.size(); i++) {
                String[] row = new String[headers.size()];
                row[0] = String.valueOf(y.get(i));
                row[1] = ""; // x坐标
                row[2] = ""; // y坐标
                row[3] = ""; // device
                row[4] = ""; // time_slot
                row[5] = ""; // heading
                
                double[] rssiVector = X.get(i);
                for (int j = 0; j < rssiVector.length; j++) {
                    row[j + 6] = String.valueOf(rssiVector[j]);
                }
                
                writer.writeNext(row);
            }
        }
    }
    
    /**
     * 追加到CSV文件
     */
    private void appendToCsv(Map<String, Object> meta, double[] rssiVector) throws IOException {
        File file = new File(DATASET);
        try (CSVWriter writer = new CSVWriter(new FileWriter(file, true))) {
            String[] row = new String[6 + beaconColumns.size()];
            row[0] = String.valueOf(meta.get("region_id"));
            row[1] = String.valueOf(meta.getOrDefault("x", ""));
            row[2] = String.valueOf(meta.getOrDefault("y", ""));
            row[3] = String.valueOf(meta.getOrDefault("device", ""));
            row[4] = String.valueOf(meta.getOrDefault("time_slot", ""));
            row[5] = String.valueOf(meta.getOrDefault("heading", ""));
            
            for (int i = 0; i < rssiVector.length; i++) {
                row[i + 6] = String.valueOf(rssiVector[i]);
            }
            
            writer.writeNext(row);
        }
    }
    
    /**
     * 生成信标键名
     */
    public String keyOf(String uuid, Integer major, Integer minor) {
        return uuid + "-" + major + "-" + minor;
    }
    
    /**
     * 获取样本特征矩阵（只读）
     */
    public List<double[]> getX() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(X);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取区域标签（只读）
     */
    public List<Integer> getY() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(y);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取信标列名（只读）
     */
    public List<String> getBeaconColumns() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(beaconColumns);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取样本数量
     */
    public int getSampleCount() {
        lock.readLock().lock();
        try {
            return y.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获取信标列数
     */
    public int getBeaconColumnCount() {
        lock.readLock().lock();
        try {
            return beaconColumns.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
