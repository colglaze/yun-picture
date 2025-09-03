package com.colglaze.yunpicture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colglaze.yunpicture.model.dto.user.*;
import com.colglaze.yunpicture.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.colglaze.yunpicture.model.vo.LoginUserVO;
import com.colglaze.yunpicture.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author ColorGlaze
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-07-23 10:58:01
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param registerRequest
     * @return
     */
    Long userRegister(UserRegisterRequest registerRequest);

    /**
     * 用户登录
     *
     * @param loginRequest
     * @param request
     * @return
     */
    LoginUserVO userLogin(UserLoginRequest loginRequest, HttpServletRequest request);

    /**
     * 获取用户登录信息
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 添加用户
     * @param userAddRequest
     * @return
     */
    Long addUser(UserAddRequest userAddRequest);

    /**
     * 分页查询用户数据
     * @param userQueryRequest
     * @return
     */
    Page<UserVO> listUserVoByPage(UserQueryRequest userQueryRequest);


    /**
     * 更改用户密码
     *
     * @param passwordRequest
     * @param request
     * @return
     */
    Boolean updatePassword(PasswordRequest passwordRequest, HttpServletRequest request);

    /**
     * 获取用户vo，数据脱敏
     * @param user
     * @return
     */
    UserVO getUserVO(User user);
}
