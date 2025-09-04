package com.gyq.ble.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 采集样本请求载荷
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollectPayload {
    
    /**
     * 区域ID（必填）
     */
    private Integer region_id;  // 改为与前端一致的字段名
    
    /**
     * X坐标（可选）
     */
    private Double x;
    
    /**
     * Y坐标（可选）
     */
    private Double y;
    
    /**
     * 设备信息（可选）
     */
    private String device;
    
    /**
     * 时段（可选）
     */
    private String time_slot;  // 改为与前端一致的字段名
    
    /**
     * 朝向（可选）
     */
    private String heading;
    
    /**
     * 信标列表
     */
    private List<BeaconReading> beacons;
}
