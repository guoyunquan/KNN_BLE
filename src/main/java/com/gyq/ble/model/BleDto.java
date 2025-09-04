package com.gyq.ble.model;

import lombok.Data;

import java.util.List;
@Data
public class BleDto {
    private List<BeaconReading> dto;
    private Integer count;
    /**
     * 区域名称
     */
    private String regional;
}
