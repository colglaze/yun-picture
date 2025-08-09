package com.colglaze.yunpicture.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/*
@author ColGlaze
@create 2025-08-09 -16:49
*/
@Data
public class PasswordRequest implements Serializable {

    /**
     * 原密码
     */
    private String userPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认密码
     */
    private String checkPassword;
}
