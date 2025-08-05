package com.colglaze.yunpicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.colglaze.yunpicture.constant.UserConstant;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.manager.FileManager;
import com.colglaze.yunpicture.model.dto.file.UploadPictureResult;
import com.colglaze.yunpicture.model.dto.picture.PictureQueryRequest;
import com.colglaze.yunpicture.model.dto.picture.PictureReviewRequest;
import com.colglaze.yunpicture.model.dto.picture.PictureUploadRequest;
import com.colglaze.yunpicture.model.entity.Picture;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.enums.PictureReviewStatusEnum;
import com.colglaze.yunpicture.model.vo.PictureVO;
import com.colglaze.yunpicture.model.vo.UserVO;
import com.colglaze.yunpicture.service.PictureService;
import com.colglaze.yunpicture.mapper.PictureMapper;
import com.colglaze.yunpicture.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author ColorGlaze
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-07-28 16:34:36
 */
@Service
@RequiredArgsConstructor
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    private final FileManager fileManager;
    private final UserService userService;

    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        //参数校验
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(pictureUploadRequest, multipartFile), ErrorCode.PARAMS_ERROR);
        //校验用户是否登录
        ThrowUtils.throwIf(ObjectUtil.isEmpty(loginUser), ErrorCode.NOT_LOGIN_ERROR);
        //上传图片，得到信息
        //按照用户id划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        UploadPictureResult pictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        //构造入库信息
        Picture picture = Picture.builder().userId(loginUser.getId()).name(pictureResult.getPicName()).build();
        this.fillReviewParams(picture, loginUser);
        BeanUtil.copyProperties(pictureResult, picture);
        //pictureId不为空，更新，补充id和编辑时间
        Long pictureId = pictureUploadRequest.getId();
        if (ObjectUtil.isNotEmpty(pictureId)) {
            //权限校验
            if (ObjectUtil.equal(picture.getUserId(), loginUser.getId()) ||
                    StrUtil.equals(loginUser.getUserRole(), UserConstant.ADMIN_ROLE)) {
                picture.setId(pictureId);
                picture.setEditTime(LocalDateTime.now());
            }
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只有本人和管理员才可以编辑图片");
        }
        //否则直接入库
        boolean update = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "图片上传失败");
        return PictureVO.objToVo(picture);
    }

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    public Page<Picture> listPictureByPage(PictureQueryRequest queryRequest, boolean isDefault) {
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        //参数校验
        if (ObjectUtil.hasEmpty(current, pageSize)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //审核条件构建
        if (isDefault) {
            queryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        }
        //构建查询条件
        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ObjectUtil.isNotEmpty(queryRequest.getId()), Picture::getId, queryRequest.getId())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getUserId()), Picture::getUserId, queryRequest.getUserId())
                .like(StrUtil.isNotBlank(queryRequest.getName()), Picture::getName, queryRequest.getName())
                .like(StrUtil.isNotBlank(queryRequest.getIntroduction()), Picture::getIntroduction, queryRequest.getIntroduction())
                .like(StrUtil.isNotBlank(queryRequest.getPicFormat()), Picture::getPicFormat, queryRequest.getPicFormat())
                .eq(StrUtil.isNotBlank(queryRequest.getCategory()), Picture::getCategory, queryRequest.getCategory())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicWidth()), Picture::getPicWidth, queryRequest.getPicWidth())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicHeight()), Picture::getPicHeight, queryRequest.getPicHeight())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicSize()), Picture::getPicSize, queryRequest.getPicSize())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicScale()), Picture::getPicScale, queryRequest.getPicScale())
                .eq(isDefault || ObjectUtil.isNotEmpty(queryRequest.getReviewStatus())
                        , Picture::getReviewStatus, queryRequest.getReviewStatus());
        if (StrUtil.isNotBlank(queryRequest.getSearchText())) {
            queryWrapper.and(qw -> {
                qw.like(Picture::getIntroduction, queryRequest.getIntroduction())
                        .or()
                        .like(Picture::getName, queryRequest.getSearchText());
            });
        }
        if (ArrayUtil.isNotEmpty(queryRequest.getTags())) {
            for (String tag : queryRequest.getTags()) {
                queryWrapper.like(Picture::getTags, tag);
            }
        }
        //排序
        queryWrapper.orderBy(true, false, Picture::getCreateTime);
        Page<Picture> page = this.page(new Page<>(current, pageSize), queryWrapper);

        return page;
    }

    @Override
    public Page<PictureVO> listPictureVOByPage(PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        boolean isDefault = false;
        if (ObjectUtil.isEmpty(request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE))) {
            isDefault = true;
        } else {
            if (StrUtil.equals(userService.getLoginUser(request).getUserRole(), UserConstant.DEFAULT_ROLE)) {
                isDefault = true;
            }
        }
        Page<Picture> picturePage = listPictureByPage(pictureQueryRequest, isDefault);
        //封装数据
        Page<PictureVO> voPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        List<Picture> records = picturePage.getRecords();
        if (ArrayUtil.isEmpty(records)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        //构建映射关系
        List<PictureVO> voList = records.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        Set<Long> userIds = voList.stream().map(PictureVO::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));
        for (PictureVO pictureVO : voList) {
            UserVO userVO = new UserVO();
            BeanUtil.copyProperties(userMap.get(pictureVO.getUserId()), userVO);
            pictureVO.setUser(userVO);
        }
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        //获取审核状态
        PictureReviewStatusEnum pictureStatus = PictureReviewStatusEnum
                .getEnumByValue(pictureReviewRequest.getReviewStatus());

        //判断是否存在
        Picture picture = this.getById(pictureReviewRequest.getId());
        if (ObjectUtil.isEmpty(picture)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        //判断审核状态是否为未审核
        if (ObjectUtil.notEqual(pictureStatus.getValue(), picture.getReviewStatus())) {
            //更新
            picture.setReviewStatus(pictureStatus.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(LocalDateTime.now());
            this.updateById(picture);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (StrUtil.equals(loginUser.getUserRole(), UserConstant.ADMIN_ROLE)) {
            //管理员自动过审
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(LocalDateTime.now());
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

}




