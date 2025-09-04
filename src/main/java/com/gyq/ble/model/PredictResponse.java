package com.gyq.ble.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.ArrayList;

/**
 * 预测响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PredictResponse {
    
    /**
     * Top-1预测区域
     */
    private Integer regionTop1;
    
    /**
     * Top-3预测区域（带分数）
     */
    private List<RegionScore> regionTop3 = new ArrayList<>();
    
    /**
     * 区域分数
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RegionScore {
        /**
         * 区域ID
         */
        private Integer regionId;
        
        /**
         * 预测分数
         */
        private Double score;
    }

}
