package com.gyq.ble.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 采集样本响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollectResponse {
    
    /**
     * 是否成功
     */
    private boolean ok;
    
    /**
     * 保存的样本数
     */
    private int saved;
    
    /**
     * 信标列数
     */
    private int beaconCols;
}
