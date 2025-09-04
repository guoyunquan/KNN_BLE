package com.gyq.ble.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 信标读取数据模型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BeaconReading {
    
    /**
     * 信标UUID
     */
    private String uuid;
    
    /**
     * 主标识符
     */
    private Integer major;
    
    /**
     * 次标识符
     */
    private Integer minor;
    
    /**
     * 信号强度指示器
     */
    private Integer rssi;
}
