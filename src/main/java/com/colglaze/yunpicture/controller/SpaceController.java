package com.colglaze.yunpicture.controller;

import com.colglaze.yunpicture.annotation.AuthCheck;
import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.ResultUtils;
import com.colglaze.yunpicture.constant.UserConstant;
import com.colglaze.yunpicture.model.dto.space.SpaceAddRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceEditRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceQueryRequest;
import com.colglaze.yunpicture.model.dto.space.SpaceUpdateRequest;
import com.colglaze.yunpicture.service.SpaceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

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

    @ApiOperation("创造空间")
    @PostMapping("/create")
    public BaseResponse<Boolean> createSpace(@RequestBody SpaceAddRequest spaceAddRequest) {
        return ResultUtils.success(spaceService.createSpace(spaceAddRequest));
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
}
