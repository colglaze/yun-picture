package com.colglaze.yunpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.colglaze.yunpicture.constant.UserConstant;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.model.dto.space.SpaceAddRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceQueryRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUpdateRequest;
import com.colglaze.yunpicture.model.entity.Space;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.enums.SpaceLevelEnum;
import com.colglaze.yunpicture.service.SpaceService;
import com.colglaze.yunpicture.mapper.SpaceMapper;
import com.colglaze.yunpicture.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;


/**
* @author ColorGlaze
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-08-09 15:42:10
*/
@Service
@RequiredArgsConstructor
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    private final UserService userService;

    @Override
    public Boolean createSpace(SpaceAddRequest spaceAddRequest) {
        Space space = new Space();
        BeanUtil.copyProperties(spaceAddRequest,space);
        validSpace(space,true);
        fillSpaceBySpaceLevel(space);
        boolean save = this.save(space);
        return save;
    }



    @Override
    public Boolean deleteSpace(SpaceQueryRequest queryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        //校验身份
        if (StrUtil.equals(loginUser.getUserRole(), UserConstant.ADMIN_ROLE)
                || ObjectUtil.equal(loginUser.getId(),queryRequest.getUserId())) {
            boolean update = this.removeById(queryRequest.getId());
            return update;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"权限不足");
    }

    @Override
    public Boolean updateSpace(SpaceUpdateRequest updateRequest) {
        Space space = this.getById(updateRequest.getId());
        BeanUtil.copyProperties(updateRequest, space, true);
        fillSpaceBySpaceLevel(space);
        boolean update = this.updateById(space);
        return update;
    }

    @Override
    public Boolean editSpace(SpaceEditRequest editRequest, HttpServletRequest request) {
        Space space = this.getById(editRequest.getId());
        BeanUtil.copyProperties(editRequest, space, true);
        validSpace(space, false);
        User loginUser = userService.getLoginUser(request);
        if (ObjectUtil.equal(loginUser.getId(), space.getUserId())) {
            boolean update = this.updateById(space);
            return update;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"只有本人可以编辑空间");
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    /**
     * 校验空间
     * @param space
     * @param add
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }

}




