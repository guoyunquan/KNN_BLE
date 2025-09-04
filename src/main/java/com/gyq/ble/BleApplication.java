package com.gyq.ble;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BLE Beacon KNN Indoor Localization Backend Application
 * 
 * 启动命令：mvn spring-boot:run
 * 
 * @author gyq
 */
@SpringBootApplication
public class BleApplication {

    public static void main(String[] args) {
        SpringApplication.run(BleApplication.class, args);
    }
}
