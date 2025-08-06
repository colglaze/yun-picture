package com.colglaze.yunpicture.manager;

import cn.hutool.core.util.ObjectUtil;
import com.baidu.aip.imageclassify.AipImageClassify;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.model.dto.picture.ImageMetadata;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageMetadataService {

    private final AipImageClassify aipImageClassify;

    /**
     * 生成图片的元数据（名称、简介、分类、标签）
     * 使用最新的百度AI接口
     */
    public ImageMetadata generateMetadata(MultipartFile file) throws IOException {
        if (ObjectUtil.isEmpty(file)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        byte[] imageData = file.getBytes();

        // 调用通用物体和场景识别API（替代原scene方法）
        JSONObject generalResult = aipImageClassify.advancedGeneral(imageData, null);

        // 从结果中提取标签和场景信息
        List<String> tags = extractTags(generalResult);
        String category = extractCategory(generalResult);

        // 生成名称和简介
        String name = generateName(tags, category);
        String description = generateDescription(tags, category);

        return new ImageMetadata(name, description, category, tags);
    }

    /**
     * 从通用识别结果中提取标签
     */
    private List<String> extractTags(JSONObject result) {
        List<String> tags = new ArrayList<>();
        if (result.has("result") && !result.isNull("result")) {
            JSONArray resultArray = result.getJSONArray("result");
            // 取前5个置信度较高的标签
            for (int i = 0; i < Math.min(5, resultArray.length()); i++) {
                JSONObject item = resultArray.getJSONObject(i);
                if (item.has("keyword")) {
                    tags.add(item.getString("keyword"));
                }
            }
        }
        return tags;
    }

    /**
     * 从通用识别结果中提取场景分类
     */
    private String extractCategory(JSONObject result) {
        // 优先使用场景信息
        if (result.has("result") && !result.isNull("result")) {
            JSONArray resultArray = result.getJSONArray("result");
            for (int i = 0; i < resultArray.length(); i++) {
                JSONObject item = resultArray.getJSONObject(i);
                // 场景类别的置信度通常较低，但更符合分类需求
                if (item.has("root") && !item.getString("root").equals("null")) {
                    return item.getString("root");
                }
            }
        }
        return "其他";
    }

    /**
     * 生成图片名称
     */
    private String generateName(List<String> tags, String category) {
        if (!tags.isEmpty()) {
            return category + " - " + tags.get(0);
        }
        return "未命名图片";
    }

    /**
     * 生成图片简介
     */
    private String generateDescription(List<String> tags, String category) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是一张").append(category).append("图片，");

        if (!tags.isEmpty()) {
            sb.append("主要包含：").append(String.join("、", tags));
        } else {
            sb.append("内容待识别");
        }

        return sb.toString();
    }
}

