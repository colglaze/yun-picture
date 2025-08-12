package com.colglaze.yunpicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PictureUpdateRequest implements Serializable {
  
    /**  
     * id  
     */  
    private Long id;  
  
    /**  
     * 图片名称  
     */  
    private String name;  
  
    /**  
     * 简介  
     */  
    private String introduction;  
  
    /**  
     *
     */  
    private String category;  
  
    /**  
     * 标签  
     */  
    private List<String> tags;

    /**
     * 空间id
     */
    private Long spaceId;
  
    private static final long serialVersionUID = 1L;  
}
