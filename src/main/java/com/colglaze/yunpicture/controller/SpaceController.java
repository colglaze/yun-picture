package com.colglaze.yunpicture.controller;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colglaze.yunpicture.annotation.AuthCheck;
import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.ResultUtils;
import com.colglaze.yunpicture.constant.UserConstant;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.manager.auth.SpaceUserAuthManager;
import com.colglaze.yunpicture.model.dto.space.SpaceAddRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceQueryRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUpdateRequest;
import com.colglaze.yunpicture.model.dto.user.UserQueryRequest;
import com.colglaze.yunpicture.model.entity.Space;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.enums.SpaceLevelEnum;
import com.colglaze.yunpicture.model.vo.SpaceLevel;
import com.colglaze.yunpicture.model.vo.SpaceVO;
import com.colglaze.yunpicture.model.vo.UserVO;
import com.colglaze.yunpicture.service.SpaceService;
import com.colglaze.yunpicture.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.colglaze.yunpicture.constant.UserConstant.ADMIN_ROLE;

/*
@author ColGlaze
@create 2025-08-10 -9:30
*/
@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "空间管理")
public class SpaceController {

    private final SpaceService spaceService;
    private final UserService userService;
    private final SpaceUserAuthManager spaceUserAuthManager;

    @ApiOperation("创造空间")
    @PostMapping("/create")
    public BaseResponse<Long> createSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        Long userId = userService.getLoginUser(request).getId();
        return ResultUtils.success(spaceService.createSpace(spaceAddRequest, userId));
    }

    @ApiOperation("删除空间")
    @DeleteMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody SpaceQueryRequest queryRequest, HttpServletRequest request) {
        return ResultUtils.success(spaceService.deleteSpace(queryRequest, request));
    }

    @ApiOperation("更新空间")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @DeleteMapping("/update")
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest updateRequest) {
        return ResultUtils.success(spaceService.updateSpace(updateRequest));
    }

    @ApiOperation("编辑空间")
    @DeleteMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest editRequest, HttpServletRequest request) {
        return ResultUtils.success(spaceService.editSpace(editRequest, request));
    }

    @GetMapping("/list/level")
    @ApiOperation("获取所有空间级别")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }

    @ApiOperation("分页查询空间")
    @PostMapping("/list/page")
    @AuthCheck(mustRole = ADMIN_ROLE)
    public BaseResponse<Page<SpaceVO>> listSpaceByPage(@RequestBody SpaceQueryRequest queryRequest){
        if (ObjectUtil.isEmpty(queryRequest)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Page<SpaceVO> page = spaceService.listSpaceByPage(queryRequest);
        return ResultUtils.success(page);
    }

    @PostMapping("/get/vo")
    @ApiOperation("根据id获取空间vo")
    public BaseResponse<SpaceVO> getSpaceVoById(@RequestBody SpaceQueryRequest queryRequest, HttpServletRequest request){
        Space space = new Space();
        if (ObjUtil.isNotEmpty(queryRequest.getId())) {
            Long id = queryRequest.getId();
            space = spaceService.getById(id);
        }else if (ObjUtil.isNotEmpty(queryRequest.getUserId())) {
            Long userId = queryRequest.getUserId();
            space = spaceService.lambdaQuery().eq(Space::getUserId, userId).one();
        }
        if (ObjectUtil.isEmpty(space)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        spaceVO.setPermissionList(permissionList);
        return ResultUtils.success(spaceVO);
    }

}
