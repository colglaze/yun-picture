package com.colglaze.yunpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.colglaze.yunpicture.constant.RedisConstant;
import com.colglaze.yunpicture.manager.auth.StpKit;
import com.colglaze.yunpicture.model.dto.user.*;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.model.vo.LoginUserVO;
import com.colglaze.yunpicture.model.vo.UserVO;

import com.colglaze.yunpicture.service.UserService;
import com.colglaze.yunpicture.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.stream.Collectors;

import static com.colglaze.yunpicture.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author ColorGlaze
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-07-23 10:58:01
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    private final StringRedisTemplate redisTemplate;

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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"用户注册失败");
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
        String password = DigestUtils.md5Hex(loginRequest.getUserPassword());
        if (ObjectUtil.notEqual(one.getUserPassword(),password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码错误");
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(one,loginUserVO);
        //记住用户登陆状态
        request.getSession().setAttribute(USER_LOGIN_STATE,one);
        // 4. 记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        //用户springSession掉了之后会自动退出登录，无须在意权限
        StpKit.SPACE.login(one.getId());
        StpKit.SPACE.getSession().set(USER_LOGIN_STATE, one);

        return loginUserVO;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        User user = (User) request.getSession().getAttribute(USER_LOGIN_STATE);
        //判断是否登录
        if (ObjectUtil.hasEmpty(user,user.getId())) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"请先登录");
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
        redisTemplate.delete(RedisConstant.USER_ROLE_GET + user.getId());
        return true;
    }

    @Override
    public Long addUser(UserAddRequest userAddRequest) {
        //参数校验
        if (ObjectUtil.isEmpty(userAddRequest)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String password = DigestUtils.md5Hex("12345678");
        User user = User.builder().userPassword(password).build();
        BeanUtil.copyProperties(userAddRequest,user);
        this.save(user);
        return user.getId();
    }

    @Override
    public Page<UserVO> listUserVoByPage(UserQueryRequest userQueryRequest) {
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        //构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper();
        queryWrapper
                .eq(ObjectUtil.isNotEmpty(userQueryRequest.getId()),User::getId,userQueryRequest.getId())
                .like(StrUtil.isNotBlank(userQueryRequest.getUserName()),User::getUserName,userQueryRequest.getUserName())
                .like(StrUtil.isNotBlank(userQueryRequest.getUserAccount()),User::getUserAccount,userQueryRequest.getUserAccount())
                .like(StrUtil.isNotBlank(userQueryRequest.getUserProfile()),User::getUserProfile,userQueryRequest.getUserProfile())
                .like(StrUtil.isNotBlank(userQueryRequest.getUserRole()),User::getUserRole,userQueryRequest.getUserRole())
                .orderBy(true,true,User::getId);
        //创建返回分页
        Page<User> page = this.page(new Page<>(current, pageSize), queryWrapper);
        Page<UserVO> userVOPage = new Page<>(current, pageSize, page.getTotal());
        //使用stream流将list<user>转换为list<userVo>
        List<UserVO> userVos = page.getRecords().stream().map(user -> {
            UserVO userVO = new UserVO();
            BeanUtil.copyProperties(user, userVO);
            return userVO;
        }).collect(Collectors.toList());
        //返回结果
        userVOPage.setRecords(userVos);
        return userVOPage;
    }

    @Override
    public Boolean updatePassword(PasswordRequest passwordRequest, HttpServletRequest request) {
        User loginUser = this.getLoginUser(request);
        String userPassword = loginUser.getUserPassword();
        //加密
        String checkPassword = DigestUtils.md5Hex(passwordRequest.getCheckPassword());
        String newPassword = DigestUtils.md5Hex(passwordRequest.getNewPassword());
        String oldPassword = DigestUtils.md5Hex(passwordRequest.getUserPassword());
        if (StrUtil.hasBlank(newPassword,
                checkPassword, oldPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码不能为空");
        }
        if (StrUtil.equals(oldPassword,userPassword)) {
            if (StrUtil.equals(newPassword,checkPassword)) {
                loginUser.setUserPassword(newPassword);
                boolean update = this.updateById(loginUser);
                return update;
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次密码输入不一致");
        }
       throw new BusinessException(ErrorCode.PARAMS_ERROR,"原密码错误");
    }
}




