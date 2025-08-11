package com.colglaze.yunpicture.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/*
@author ColGlaze
@create 2025-08-11 -18:34
*/
public class CaffeineUtil {
    public static final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 10 分钟移除
                    .expireAfterWrite(10L, TimeUnit.MINUTES)
                    .build();

}
