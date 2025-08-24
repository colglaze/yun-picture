package com.colglaze.yunpicture.controller;

import cn.hutool.core.util.ObjectUtil;
import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.DeleteRequest;
import com.colglaze.yunpicture.common.ResultUtils;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.manager.auth.annotation.SaSpaceCheckPermission;
import com.colglaze.yunpicture.manager.auth.constant.SpaceUserPermissionConstant;
import com.colglaze.yunpicture.model.dto.space.SpaceEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUserAddRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUserEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUserQueryRequest;
import com.colglaze.yunpicture.model.entity.SpaceUser;
import com.colglaze.yunpicture.model.vo.SpaceUserVO;
import com.colglaze.yunpicture.service.SpaceUserService;
import com.colglaze.yunpicture.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/*
@author ColGlaze
@create 2025-08-20 -16:15
*/
@RequiredArgsConstructor
@RequestMapping("/spaceUser")
@Api(tags = "团队空间")
@RestController
public class SpaceUserController {

    private final SpaceUserService spaceUserService;

    @PostMapping("/add")
    @ApiOperation("添加成员到空间")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(ObjectUtil.isEmpty(spaceUserAddRequest), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(spaceUserService.addSpaceUser(spaceUserAddRequest, request));
    }

    @PostMapping("/delete")
    @ApiOperation("从空间移除成员")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(ObjectUtil.isEmpty(deleteRequest), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(spaceUserService.deleteSpaceUser(deleteRequest, request));
    }

    @PostMapping("/get")
    @ApiOperation("获取单个空间成员的信息")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        ThrowUtils.throwIf(ObjectUtil.isEmpty(spaceUserQueryRequest), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(spaceUserService.getSpaceUser(spaceUserQueryRequest));
    }

    @PostMapping("/get/list")
    @ApiOperation("获取空间成员信息列表")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        ThrowUtils.throwIf(ObjectUtil.isEmpty(spaceUserQueryRequest), ErrorCode.PARAMS_ERROR);
        List<SpaceUserVO> spaceUserVOS = spaceUserService.listSpaceUser(spaceUserQueryRequest);
        return ResultUtils.success(spaceUserVOS);
    }

    @PostMapping("/edit")
    @ApiOperation("编辑成员信息")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest editRequest) {
        ThrowUtils.throwIf(ObjectUtil.isEmpty(editRequest), ErrorCode.PARAMS_ERROR);
        Boolean edit = spaceUserService.editSpaceUser(editRequest);
        return ResultUtils.success(edit);
    }

    @PostMapping("/list/my")
    @ApiOperation("查询我加入的团队空间列表")
    public BaseResponse<List<SpaceUserVO>> listMyJoinSpace(HttpServletRequest request) {
        return ResultUtils.success(spaceUserService.listMyJoinSpace(request));
    }

}
