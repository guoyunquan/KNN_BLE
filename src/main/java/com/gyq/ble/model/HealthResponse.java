package com.gyq.ble.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 健康检查响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HealthResponse {
    
    /**
     * 服务状态
     */
    private String status;
    
    /**
     * 信标列数
     */
    private int beaconCols;
    
    /**
     * 样本数量
     */
    private int samples;
}
