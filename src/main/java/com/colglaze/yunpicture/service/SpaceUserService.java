package com.colglaze.yunpicture.service;

import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.DeleteRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUserAddRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUserEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUserQueryRequest;
import com.colglaze.yunpicture.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.colglaze.yunpicture.model.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author ColorGlaze
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2025-08-20 16:05:58
*/
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 空间成员的权限校验
     * @param spaceUser
     * @param add
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 添加成员到空间
     * @param spaceUserAddRequest
     * @param request
     * @return
     */
    Long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest, HttpServletRequest request);

    /**
     * 从空间移除成员
     * @param deleteRequest
     * @param request
     * @return
     */
    Boolean deleteSpaceUser(DeleteRequest deleteRequest, HttpServletRequest request);

    /**
     * 获取单个空间内成员的信息
     * @param spaceUserQueryRequest
     * @return
     */
    SpaceUser getSpaceUser(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 查询空间成员列表
     * @param spaceUserQueryRequest
     * @return
     */
    List<SpaceUserVO> listSpaceUser(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 编辑成员信息
     * @param editRequest
     * @return
     */
    Boolean editSpaceUser(SpaceUserEditRequest editRequest);

    /**
     * 查询我家路的团队空间
     * @param request
     * @return
     */
    List<SpaceUserVO> listMyJoinSpace(HttpServletRequest request);

}
