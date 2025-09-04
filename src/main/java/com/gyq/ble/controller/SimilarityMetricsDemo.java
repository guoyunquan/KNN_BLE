package com.gyq.ble.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class SimilarityMetricsDemo {
    public static Double cosDouble(String jsonA, String jsonB) {

        // 1) 解析 JSON 为 Map<String, Double>
        Map<String, Double> mapA = parseToMap(jsonA);
        Map<String, Double> mapB = parseToMap(jsonB);

        // 2) 取交集并排序，保证对齐
        List<String> commonKeys = getCommonKeysSorted(mapA, mapB);
        if (commonKeys.isEmpty()) {
            System.out.println("没有共同ID，无法比较。");
            return null;
        }

        // 3) 映射为向量（按相同顺序）
        double[] a = toVector(mapA, commonKeys);
        double[] b = toVector(mapB, commonKeys);

        // 打印对齐后的键值核验
        System.out.println("共同ID数量: " + commonKeys.size());
        System.out.println("共同ID(排序后): " + commonKeys);
        System.out.println("\n对齐后的 (key, A, B)：");
        for (int i = 0; i < commonKeys.size(); i++) {
            System.out.printf("%s => A: %s, B: %s%n", commonKeys.get(i), a[i], b[i]);
        }

        // 4) 计算与打印各类指标
        System.out.println("\n=== 指标（原始 dBm 向量）===");
        System.out.printf("Cosine 余弦相似度           : %.6f%n", cosine(a, b));
        System.out.printf("Mean-Centered Cosine(中心化): %.6f%n", centeredCosine(a, b));
        System.out.printf("Pearson 皮尔逊相关          : %.6f%n", pearson(a, b));
        System.out.printf("Spearman 秩相关             : %.6f%n", spearman(a, b));
        System.out.printf("L2 欧氏距离                 : %.6f%n", euclidean(a, b));
        System.out.printf("L1 曼哈顿距离               : %.6f%n", manhattan(a, b));
        System.out.printf("MAE 平均绝对误差            : %.6f%n", mae(a, b));

        // 5) 可选：将 dBm 转为线性功率（mW），再做余弦
        double[] aMw = dbmToMilliwatt(a);
        double[] bMw = dbmToMilliwatt(b);
        System.out.println("\n=== 可选：转为线性功率(mW)后的相似度 ===");
        System.out.printf("Cosine(功率mW)              : %.6f%n", cosine(aMw, bMw));
        return null;
    }

    /**
     * 将 JSON 字符串解析为 Map<String, Double>（仅保留可转为数值的项）
     */
    public static Map<String, Double> parseToMap(String jsonStr) {
        JSONObject obj = JSON.parseObject(jsonStr);
        Map<String, Double> map = new HashMap<>();
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Number) {
                map.put(e.getKey(), ((Number) v).doubleValue());
            } else if (v instanceof String) {
                try {
                    map.put(e.getKey(), Double.parseDouble((String) v));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return map;
    }

    /**
     * 求共同 key，并按字典序排序，保证向量对齐一致
     */
    public static List<String> getCommonKeysSorted(Map<String, Double> a, Map<String, Double> b) {
        return a.keySet().stream().filter(b::containsKey).sorted().collect(Collectors.toList());
    }

    /**
     * 按给定顺序取值映射为向量
     */
    public static double[] toVector(Map<String, Double> map, List<String> keys) {
        double[] v = new double[keys.size()];
        for (int i = 0; i < keys.size(); i++) v[i] = map.getOrDefault(keys.get(i), 0.0);
        return v;
    }

    /* ===================== 距离 / 相似度函数 ===================== */

    /**
     * 余弦相似度 [-1,1]
     */
    public static double cosine(double[] x, double[] y) {
        checkSameLen(x, y);
        double dot = 0, nx = 0, ny = 0;
        for (int i = 0; i < x.length; i++) {
            dot += x[i] * y[i];
            nx += x[i] * x[i];
            ny += y[i] * y[i];
        }
        if (nx == 0 || ny == 0) return 0.0;
        return dot / (Math.sqrt(nx) * Math.sqrt(ny));
    }

    /**
     * 中心化余弦：对 x,y 先减去各自均值，再做余弦（与皮尔逊的分母略有差别）
     */
    public static double centeredCosine(double[] x, double[] y) {
        checkSameLen(x, y);
        double mx = mean(x), my = mean(y);
        double dot = 0, nx = 0, ny = 0;
        for (int i = 0; i < x.length; i++) {
            double a = x[i] - mx;
            double b = y[i] - my;
            dot += a * b;
            nx += a * a;
            ny += b * b;
        }
        if (nx == 0 || ny == 0) return 0.0;
        return dot / (Math.sqrt(nx) * Math.sqrt(ny));
    }

    /**
     * 皮尔逊相关系数 [-1,1]（与中心化余弦数值相同）
     */
    public static double pearson(double[] x, double[] y) {
        // 在这里与 centeredCosine 等价
        return centeredCosine(x, y);
    }

    /**
     * Spearman 秩相关：对 x,y 分别转秩，再做皮尔逊
     */
    public static double spearman(double[] x, double[] y) {
        checkSameLen(x, y);
        double[] rx = rankWithTiesAverage(x);
        double[] ry = rankWithTiesAverage(y);
        return pearson(rx, ry);
    }

    /**
     * 欧氏距离 L2
     */
    public static double euclidean(double[] x, double[] y) {
        checkSameLen(x, y);
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            double d = x[i] - y[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /**
     * 曼哈顿距离 L1
     */
    public static double manhattan(double[] x, double[] y) {
        checkSameLen(x, y);
        double sum = 0;
        for (int i = 0; i < x.length; i++) sum += Math.abs(x[i] - y[i]);
        return sum;
    }

    /**
     * MAE 平均绝对误差
     */
    public static double mae(double[] x, double[] y) {
        return manhattan(x, y) / x.length;
    }

    /* ===================== 工具函数 ===================== */

    public static void checkSameLen(double[] x, double[] y) {
        if (x == null || y == null) throw new IllegalArgumentException("向量不能为 null");
        if (x.length != y.length) throw new IllegalArgumentException("向量长度必须相同");
    }

    public static double mean(double[] v) {
        double s = 0;
        for (double x : v) s += x;
        return s / v.length;
    }

    /**
     * dBm -> mW：P(mW) = 10^(dBm/10)
     */
    public static double[] dbmToMilliwatt(double[] dbm) {
        double[] out = new double[dbm.length];
        for (int i = 0; i < dbm.length; i++) out[i] = Math.pow(10.0, dbm[i] / 10.0);
        return out;
    }

    /**
     * 生成平均秩（ties 取平均）：返回与原数组同长度的秩数组。
     * 例：原值 [5, 10, 10, 20] -> 秩 [1, 2.5, 2.5, 4]
     */
    public static double[] rankWithTiesAverage(double[] arr) {
        int n = arr.length;
        double[] ranks = new double[n];

        // 记录原索引
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;

        // 升序排序索引（按值）
        Arrays.sort(idx, Comparator.comparingDouble(i -> arr[i]));

        int i = 0;
        while (i < n) {
            int j = i;
            // 找到一段相等值的区间 [i, j)
            while (j + 1 < n && Double.compare(arr[idx[j + 1]], arr[idx[i]]) == 0) j++;
            // 区间的秩为 1-based：平均 (i+1 ... j+1)
            double rank = (i + 1 + j + 1) / 2.0;
            for (int k = i; k <= j; k++) ranks[idx[k]] = rank;
            i = j + 1;
        }
        return ranks;
    }
}
