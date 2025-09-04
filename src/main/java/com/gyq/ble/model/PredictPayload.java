package com.gyq.ble.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 预测请求载荷
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PredictPayload {
    
    /**
     * 信标列表
     */
    private List<BeaconReading> beacons;
}
