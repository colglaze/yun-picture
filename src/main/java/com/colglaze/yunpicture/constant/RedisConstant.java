package com.colglaze.yunpicture.constant;

/*
@author ColGlaze
@create 2025-08-08 -22:11
*/
public interface RedisConstant {
    //分页查询图片列表的key
    String LIST_PICTURE_BY_PAGE = "yunPicture:listPictureVoByPage:";
    //分页查询图片列表的key的前缀索引
    String LIST_PICTURE_BY_PAGE_INDEX = "listPictureVoByPage";

    //获取标签分类的key
    String GET_CATE_AND_TAGS = "yunPicture:getCateAndTags";

}
