package com.colglaze.yunpicture.service;

import com.colglaze.yunpicture.model.dto.space.SpaceAddRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceQueryRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUpdateRequest;
import com.colglaze.yunpicture.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;

/**
* @author ColorGlaze
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-08-09 15:42:10
*/
public interface SpaceService extends IService<Space> {

    /**
     * 创造空间
     *
     * @param spaceAddRequest
     * @param userId
     * @return
     */
    Long createSpace(SpaceAddRequest spaceAddRequest, Long userId);

    /**
     * 删除空间
     *
     * @param queryRequest
     * @param request
     * @return
     */
    Boolean deleteSpace(SpaceQueryRequest queryRequest, HttpServletRequest request);

    /**
     * 更新空间
     * @param updateRequest
     * @return
     */
    Boolean updateSpace(SpaceUpdateRequest updateRequest);

    /**
     * 编辑空间
     *
     * @param editRequest
     * @param request
     * @return
     */
    Boolean editSpace(SpaceEditRequest editRequest, HttpServletRequest request);

    /**
     * 填充参数
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 校验空间参数
     * @param space
     * @param add
     */
    void validSpace(Space space, boolean add);
}
