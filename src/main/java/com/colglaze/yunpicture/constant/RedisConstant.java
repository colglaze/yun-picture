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

    //单条图片数据缓存键前缀
    String PICTURE_SINGLE_KEY = "picture:single:";
    //图片数据全局版本号键
    String PICTURE_VERSION_KEY = "picture:version";
    // 按空间维度的版本号前缀
    String PICTURE_VERSION_SPACE_PREFIX = "picture:version:space:";
    // 按用户维度的版本号前缀（备用，如需要按用户页面失效）
    String PICTURE_VERSION_USER_PREFIX = "picture:version:user:";

    //用于存储当前登录用户的角色
    String USER_ROLE_GET = "user_role_get";

}
