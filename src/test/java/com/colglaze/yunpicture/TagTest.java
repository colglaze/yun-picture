package com.colglaze.yunpicture;

import com.colglaze.yunpicture.mapper.PictureMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
@author ColGlaze
@create 2025-08-25 -9:54
*/
@SpringBootTest
public class TagTest {
    @Resource
    PictureMapper pictureMapper;
    // JSON解析器（可注入Spring容器）
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Test
    void test01(){
        List<String> top10TagNames = getTop10TagNames();
        for (String top10TagName : top10TagNames) {
            System.out.println(top10TagName);
        }

    }

    public List<String> getTop10TagNames() {
        // 1. 获取原始标签数据
        List<String> tagJsonList = pictureMapper.getTags();
        if (tagJsonList == null || tagJsonList.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 解析JSON并统计频次
        Map<String, Integer> tagCountMap = new HashMap<>();
        for (String tagJson : tagJsonList) {
            try {
                List<String> tagList = objectMapper.readValue(tagJson, new TypeReference<List<String>>() {});
                for (String tag : tagList) {
                    if (tag != null && !tag.trim().isEmpty()) {
                        tagCountMap.put(tag, tagCountMap.getOrDefault(tag, 0) + 1);
                    }
                }
            } catch (Exception e) {
                System.err.println("解析标签JSON失败：" + tagJson + "，错误：" + e.getMessage());
            }
        }

        // 3. 按频次排序后，仅提取标签名称（取前12）
        return tagCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()) // 按次数降序
                .limit(12) // 取前12
                .map(Map.Entry::getKey) // 只保留标签名称
                .collect(Collectors.toList());
    }
}
