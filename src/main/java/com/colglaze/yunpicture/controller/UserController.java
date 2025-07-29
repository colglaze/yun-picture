package com.colglaze.yunpicture.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colglaze.yunpicture.annotation.AuthCheck;
import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.DeleteRequest;
import com.colglaze.yunpicture.common.PageRequest;
import com.colglaze.yunpicture.common.ResultUtils;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.model.dto.user.*;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.vo.LoginUserVO;
import com.colglaze.yunpicture.model.vo.UserVO;
import com.colglaze.yunpicture.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.yaml.snakeyaml.events.Event;


import javax.servlet.http.HttpServletRequest;

import static com.colglaze.yunpicture.constant.UserConstant.ADMIN_ROLE;

/*
@author ColGlaze
@create 2025-07-23 -11:14
*/
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Api(tags = "用户相关接口")
public class UserController {

    private final UserService userService;

    @ApiOperation("用户注册")
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest registerRequest) {
        //参数校验
        if (ObjectUtil.isEmpty(registerRequest)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long result = userService.userRegister(registerRequest);
        return ResultUtils.success(result);
    }

    @ApiOperation("用户登录")
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest loginRequest, HttpServletRequest request) {
        //参数校验
        if (ObjectUtil.isEmpty(loginRequest)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LoginUserVO loginUserVO = userService.userLogin(loginRequest, request);
        return ResultUtils.success(loginUserVO);
    }


    @ApiOperation("获取当前登录用户信息")
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        LoginUserVO userVO = new LoginUserVO();
        BeanUtil.copyProperties(loginUser, userVO);
        return ResultUtils.success(userVO);
    }


    @ApiOperation("用户注销")
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    @ApiOperation("管理员添加用户")
    @PostMapping("/add")
    @AuthCheck(mustRole = ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        Long userId = userService.addUser(userAddRequest);
        return ResultUtils.success(userId);
    }

    @ApiOperation("根据id获取用户信息")
    @PostMapping("/get")
    @AuthCheck(mustRole = ADMIN_ROLE)
    public BaseResponse<User> getById(long id) {
        ThrowUtils.throwIf(id < 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(ObjectUtil.isEmpty(user), ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    @ApiOperation("用户根据id获取用户vo")
    @PostMapping("get/vo")
    public BaseResponse<UserVO> getVoById(Long id) {
        User user = userService.getById(id);
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return ResultUtils.success(userVO);
    }

    @ApiOperation("根据id删除用户，限管理员")
    @DeleteMapping("/delete")
    @AuthCheck(mustRole = ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (ObjectUtil.isEmpty(deleteRequest)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean remove = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(remove);
    }

    @ApiOperation("更新用户信息")
    @PostMapping("/update")
//    @AuthCheck(mustRole = ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest updateRequest) {
        if (ObjectUtil.isEmpty(updateRequest)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = User.builder().build();
        BeanUtil.copyProperties(updateRequest,user);
        boolean update = userService.updateById(user);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return ResultUtils.success(update);
    }

    @ApiOperation("分页查询用户信息")
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVoByPage(@RequestBody UserQueryRequest userQueryRequest){
        if (ObjectUtil.isEmpty(userQueryRequest)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Page<UserVO> userVOPage = userService.listUserVoByPage(userQueryRequest);
        return ResultUtils.success(userVOPage);
    }
}
