package com.colglaze.yunpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.manager.FileManager;
import com.colglaze.yunpicture.model.dto.file.UploadPictureResult;
import com.colglaze.yunpicture.model.dto.picture.PictureUploadRequest;
import com.colglaze.yunpicture.model.entity.Picture;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.vo.PictureVO;
import com.colglaze.yunpicture.service.PictureService;
import com.colglaze.yunpicture.mapper.PictureMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;

/**
* @author ColorGlaze
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-07-28 16:34:36
*/
@Service
@RequiredArgsConstructor
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    private final FileManager fileManager;

    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        //参数校验
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(pictureUploadRequest,multipartFile),ErrorCode.PARAMS_ERROR);
        //校验用户是否登录
        ThrowUtils.throwIf(ObjectUtil.isEmpty(loginUser), ErrorCode.NOT_LOGIN_ERROR);
        //上传图片，得到信息
        //按照用户id划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        UploadPictureResult pictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        //构造入库信息
        Picture picture = Picture.builder().userId(loginUser.getId()).build();
        BeanUtil.copyProperties(pictureResult,picture);
        //pictureId不为空，更新，补充id和编辑时间
        Long pictureId = pictureUploadRequest.getId();
        if (ObjectUtil.isNotEmpty(pictureId)) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        //否则直接入库
        boolean update = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(update,ErrorCode.OPERATION_ERROR,"图片上传失败");
        return PictureVO.objToVo(picture);
    }
}




