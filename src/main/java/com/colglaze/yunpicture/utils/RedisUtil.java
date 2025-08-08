package com.colglaze.yunpicture.utils;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/*
@author ColGlaze
@create 2025-08-08 -22:15
*/
@RequiredArgsConstructor
@Component
public class RedisUtil {
    private final StringRedisTemplate stringRedisTemplate;
    //  刷新时，从索引中获取所有key并操作
    public void refreshByIndex(String indexKey) {
        // 获取索引中所有key
        Set<String> keys = stringRedisTemplate.opsForSet().members(indexKey);
        if (CollUtil.isNotEmpty(keys)) {
            // 批量删除缓存
            stringRedisTemplate.delete(keys);
            // 同时清空索引（可选，根据业务需要）
            stringRedisTemplate.delete(indexKey);
        }
    }
}
