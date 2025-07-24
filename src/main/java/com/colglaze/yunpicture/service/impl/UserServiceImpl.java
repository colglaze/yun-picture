package com.colglaze.yunpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.colglaze.yunpicture.model.dto.user.UserLoginRequest;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.model.dto.user.UserRegisterRequest;
import com.colglaze.yunpicture.model.vo.LoginUserVO;
import com.colglaze.yunpicture.service.UserService;
import com.colglaze.yunpicture.mapper.UserMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

import static com.colglaze.yunpicture.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author ColorGlaze
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-07-23 10:58:01
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Override
    public Long userRegister(UserRegisterRequest registerRequest) {
        if (StrUtil.hasBlank(registerRequest.getUserAccount()
                , registerRequest.getUserPassword()
                , registerRequest.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        //password length > 8
        if (registerRequest.getUserPassword().length() < 8 || registerRequest.getCheckPassword().length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度需大于8");
        }
        //userAccount is not null
        if (ObjectUtil.isEmpty(registerRequest.getUserAccount())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名不能为空");
        }
        //checkPassword is same with password
        if (ObjectUtil.notEqual(registerRequest.getUserPassword(), registerRequest.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次密码输入不一致");
        }

        //判断用户是否已经存在
        User one = lambdaQuery().eq(User::getUserAccount, registerRequest.getUserAccount()).one();
        if (ObjectUtil.isNotEmpty(one)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户已存在");
        }
        //加密
        String password = DigestUtils.md5Hex(registerRequest.getUserPassword());

        //保存数据
        User user = User.builder()
                .userAccount(registerRequest.getUserAccount())
                .userPassword(password)
                .userName("子衿用户")
                .build();
        boolean save = this.save(user);
        if (!save) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"用户保存失败");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(UserLoginRequest loginRequest, HttpServletRequest request) {
        //参数校验
        if (StrUtil.hasBlank(loginRequest.getUserAccount(),loginRequest.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名和密码不能为空");
        }
        //查询用户是否存在
        User one = lambdaQuery().eq(User::getUserAccount, loginRequest.getUserAccount()).one();
        if (ObjectUtil.isEmpty(one)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在");
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(one,loginUserVO);
        request.getSession().setAttribute(USER_LOGIN_STATE,one);
        return loginUserVO;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        User user = (User) request.getSession().getAttribute(USER_LOGIN_STATE);
        //判断是否登录
        if (ObjectUtil.hasEmpty(user,user.getId())) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User userById = this.getById(user.getId());
        if (ObjectUtil.isEmpty(userById)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return userById;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        //判断是否登录
        User user = (User) request.getSession().getAttribute(USER_LOGIN_STATE);
        if (ObjectUtil.isEmpty(user)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }


}




