package com.gyq.ble.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 重载数据集响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReloadResponse {
    
    /**
     * 是否成功
     */
    private boolean ok;
    
    /**
     * 信标列数
     */
    private int beaconCols;
    
    /**
     * 样本数量
     */
    private int samples;
}
