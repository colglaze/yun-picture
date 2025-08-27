package com.colglaze.yunpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.DeleteRequest;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.model.dto.space.SpaceEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUserAddRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUserEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUserQueryRequest;
import com.colglaze.yunpicture.model.entity.Space;
import com.colglaze.yunpicture.model.entity.SpaceUser;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.enums.SpaceRoleEnum;
import com.colglaze.yunpicture.model.vo.SpaceUserVO;
import com.colglaze.yunpicture.model.vo.SpaceVO;
import com.colglaze.yunpicture.model.vo.UserVO;
import com.colglaze.yunpicture.service.SpaceService;
import com.colglaze.yunpicture.service.SpaceUserService;
import com.colglaze.yunpicture.mapper.SpaceUserMapper;
import com.colglaze.yunpicture.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author ColorGlaze
* @description 针对表【space_user(空间用户关联)】的数据库操作Service实现
* @createDate 2025-08-20 16:05:58
*/
@Service
@RequiredArgsConstructor
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
    implements SpaceUserService{

    private final UserService userService;
    private final SpaceService spaceService;

    @Override
    public Long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest, HttpServletRequest request) {
        Long userId = userService.lambdaQuery().eq(User::getUserAccount, spaceUserAddRequest.getUserAccount()).one().getId();
        //拷贝数据
        SpaceUser spaceUser = new SpaceUser();
        BeanUtil.copyProperties(spaceUserAddRequest, spaceUser);
        spaceUser.setUserId(userId);
        //校验spaceUser是否合法
        validSpaceUser(spaceUser, true);
        boolean save = this.save(spaceUser);
        ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);
        return spaceUser.getId();
    }

    @Override
    public Boolean deleteSpaceUser(DeleteRequest deleteRequest, HttpServletRequest request) {
        //判断是否存在
        SpaceUser byId = this.getById(deleteRequest.getId());
        ThrowUtils.throwIf(ObjectUtil.isEmpty(byId), ErrorCode.NOT_FOUND_ERROR, "该成员不存在");
        boolean remove = this.removeById(deleteRequest.getId());
        return remove;
    }

    @Override
    public SpaceUser getSpaceUser(SpaceUserQueryRequest spaceUserQueryRequest) {
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        if (ObjectUtil.hasEmpty(spaceId, userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        SpaceUser spaceUser = lambdaQuery().eq(SpaceUser::getUserId, userId).eq(SpaceUser::getSpaceId, spaceId).one();
        ThrowUtils.throwIf(ObjectUtil.isEmpty(spaceUser), ErrorCode.NOT_FOUND_ERROR);
        return spaceUser;
    }

    @Override
    public List<SpaceUserVO> listSpaceUser(SpaceUserQueryRequest request) {
        //获取space user列表
        LambdaQueryWrapper<SpaceUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ObjectUtil.isNotEmpty(request.getId()), SpaceUser::getId, request.getId())
                .eq(ObjectUtil.isNotEmpty(request.getSpaceId()), SpaceUser::getSpaceId, request.getSpaceId())
                .eq(ObjectUtil.isNotEmpty(request.getUserId()), SpaceUser::getUserId, request.getUserId())
                .eq(ObjectUtil.isNotEmpty(request.getSpaceRole()), SpaceUser::getSpaceRole, request.getSpaceRole());
        List<SpaceUser> list = this.list(wrapper);
        ArrayList<SpaceUserVO> spaceUserVOS = getSpaceUserVOS(list);
        return spaceUserVOS;
    }

    /**
     * 获取spaceUserVo列表
     * @param list
     * @return
     */
    private ArrayList<SpaceUserVO> getSpaceUserVOS(List<SpaceUser> list) {
        //获取对应的用户id和空间id
        Set<Long> userIds = list.stream().map(SpaceUser::getUserId).collect(Collectors.toSet());
        Set<Long> spaceIds = list.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());
        //查询出对应的userVo和spaceVo信息
        Map<Long, UserVO> userMap = userService
                .listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> {
                    UserVO userVO = new UserVO();
                    BeanUtil.copyProperties(user, userVO);
                    return userVO;
                }));
        Map<Long, SpaceVO> spaceMap = spaceService
                .listByIds(spaceIds)
                .stream()
                .collect(Collectors.toMap(Space::getId, space -> {
                    SpaceVO spaceVO = SpaceVO.objToVo(space);
                    return spaceVO;
                }));
        //对结果进行封装
        ArrayList<SpaceUserVO> spaceUserVOS = new ArrayList<>();
        for (SpaceUser spaceUser : list) {
            SpaceUserVO spaceUserVO = new SpaceUserVO();
            BeanUtil.copyProperties(spaceUser, spaceUserVO);
            spaceUserVO.setUser(userMap.get(spaceUserVO.getUserId()));
            spaceUserVO.setSpace(spaceMap.get(spaceUserVO.getSpaceId()));
            spaceUserVOS.add(spaceUserVO);
        }
        return spaceUserVOS;
    }

    @Override
    public Boolean editSpaceUser(SpaceUserEditRequest editRequest) {
        SpaceUser spaceUser = new SpaceUser();
        BeanUtil.copyProperties(editRequest, spaceUser);
        SpaceUser byId = this.getById(editRequest.getId());
        ThrowUtils.throwIf(ObjectUtil.isEmpty(byId), ErrorCode.NOT_FOUND_ERROR);
        byId.setSpaceRole(spaceUser.getSpaceRole());
        validSpaceUser(byId, false);
        boolean update = this.updateById(byId);
        return update;
    }

    @Override
    public List<SpaceUserVO> listMyJoinSpace(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<SpaceUser> spaceUserList = lambdaQuery()
                .eq(SpaceUser::getUserId, loginUser.getId())
                .list();
        ArrayList<SpaceUserVO> spaceUserVOS = getSpaceUserVOS(spaceUserList);
        return spaceUserVOS;
    }


    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        // 创建时，空间 id 和用户 id 必填
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if (add) {
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        if (spaceRole != null && spaceRoleEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }



}




