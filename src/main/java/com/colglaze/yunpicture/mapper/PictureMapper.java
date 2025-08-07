package com.colglaze.yunpicture.mapper;

import com.colglaze.yunpicture.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colglaze.yunpicture.model.vo.PictureTagCategory;

import java.util.List;

/**
* @author ColorGlaze
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2025-07-28 16:34:36
* @Entity com.colglaze.yunpicture.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

    /**
     * 获取标签
     * @return
     */
    List<String> getTags();

    /**
     * 获取分类
     * @return
     */
    List<String> getCategory();
}




