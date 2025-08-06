package com.colglaze.yunpicture.model.dto.picture;

import java.util.List;

// 元数据实体类
public class ImageMetadata {
    private String name;
    private String introduction;
    private String category;
    private List<String> tags;

    public ImageMetadata(String name, String description, String category, List<String> tags) {
        this.name = name;
        this.introduction = description;
        this.category = category;
        this.tags = tags;
    }

    // getter和setter方法
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
