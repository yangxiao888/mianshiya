package com.yangxiao.mianshiya.blackfilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;


public class BlackIpUtils {
    private static BitMapBloomFilter bloomFilter;

    // 缓存上一次有效配置（用于降级兜底）
    private static String lastValidConfig;
    //判断IP是否在黑名单中
    public static boolean isBlackIp(String ip) {
        return bloomFilter.contains(ip);
    }

    //重建布隆过滤器/黑名单
    public static void rebuildBlackIp(String configInfo) {
        if (StrUtil.isBlank(configInfo)) {
//            configInfo = "{}";
            configInfo = lastValidConfig;
        }
        //将本次配置写入，防止下次读取配置失败
        lastValidConfig = configInfo;
        // 解析 yaml 文件
        Yaml yaml = new Yaml();
        Map map = yaml.loadAs(configInfo, Map.class);
        // 获取 ip 黑名单
        List<String> blackIpList = (List<String>) map.get("blackIpList");
        // 加锁防止并发
        synchronized (BlackIpUtils.class) {
            if (CollectionUtil.isNotEmpty(blackIpList)) {
                // 注意构造参数的设置
                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(958506);
                for (String ip : blackIpList) {
                    bitMapBloomFilter.add(ip);
                }
                bloomFilter = bitMapBloomFilter;
            } else {
                bloomFilter = new BitMapBloomFilter(100);
            }
        }

    }
}
