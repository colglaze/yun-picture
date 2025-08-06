package com.colglaze.yunpicture.config;

import com.baidu.aip.imageclassify.AipImageClassify;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AipConfig {
    
    @Value("${baidu.ai.appId}")
    private String appId;
    
    @Value("${baidu.ai.apiKey}")
    private String apiKey;
    
    @Value("${baidu.ai.secretKey}")
    private String secretKey;
    
    @Bean
    public AipImageClassify aipImageClassify() {
        // 初始化一个AipImageClassify
        AipImageClassify client = new AipImageClassify(appId, apiKey, secretKey);
        // 可选：设置网络连接参数
        client.setConnectionTimeoutInMillis(30000);
        client.setSocketTimeoutInMillis(60000);
        return client;
    }
}