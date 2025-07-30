package com.colglaze.yunpicture.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colglaze.yunpicture.annotation.AuthCheck;
import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.DeleteRequest;
import com.colglaze.yunpicture.common.ResultUtils;
import com.colglaze.yunpicture.constant.UserConstant;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.manager.CosManager;
import com.colglaze.yunpicture.model.dto.picture.PictureEditRequest;
import com.colglaze.yunpicture.model.dto.picture.PictureQueryRequest;
import com.colglaze.yunpicture.model.dto.picture.PictureUpdateRequest;
import com.colglaze.yunpicture.model.dto.picture.PictureUploadRequest;
import com.colglaze.yunpicture.model.entity.Picture;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.vo.PictureVO;
import com.colglaze.yunpicture.service.PictureService;
import com.colglaze.yunpicture.service.UserService;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Date;

/*
@author ColGlaze
@create 2025-07-28 -15:47
*/
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "文件传输")
public class FileController {

    private final CosManager cosManager;

    private final UserService userService;

    private final PictureService pictureService;

    /**
     * 测试文件上传
     *
     * @param multipartFile
     * @return
     */
    @ApiOperation("测试文件传输")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {
        // 文件目录
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("/test/%s", filename);
        File file = null;
        try {
            // 上传文件
            file = File.createTempFile(filepath, null);
            multipartFile.transferTo(file);
            cosManager.putObject(filepath, file);
            // 返回可访问地址
            return ResultUtils.success(filepath);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }

    /**
     * 测试文件下载
     *
     * @param filepath 文件路径
     * @param response 响应对象
     */
    @ApiOperation("文件下载")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download/")
    public void testDownloadFile(String filepath, HttpServletResponse response) throws IOException {
        COSObjectInputStream cosObjectInput = null;
        try {
            COSObject cosObject = cosManager.getObject(filepath);
            cosObjectInput = cosObject.getObjectContent();
            // 处理下载到的流
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);
            // 写入响应
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }
    }

    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    @ApiOperation("上传图片")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     */
    @PostMapping("/delete")
    @ApiOperation("删除图片")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        //只有管理员和本人才可以删除照片
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(deleteRequest, loginUser), ErrorCode.PARAMS_ERROR, "用户未登录或参数为空");
        Picture picture = pictureService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(ObjectUtil.isEmpty(picture), ErrorCode.NOT_FOUND_ERROR);
        if (ObjectUtil.equal(picture.getId(), loginUser.getId()) || loginUser.getUserRole() == UserConstant.ADMIN_ROLE) {
            Boolean remove = pictureService.removeById(picture);
            return ResultUtils.success(remove);
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @ApiOperation("更新图片")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest) {
        ThrowUtils.throwIf(ObjectUtil.isEmpty(pictureUpdateRequest), ErrorCode.PARAMS_ERROR);
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureUpdateRequest, picture);
        String tags = JSONUtil.toJsonStr(pictureUpdateRequest.getTags());
        picture.setTags(tags);
        //判断是否存在
        if (ObjectUtil.isEmpty(pictureService.getById(picture.getId()))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片不存在");
        }
        //校验图片信息
        pictureService.validPicture(picture);
        boolean update = pictureService.updateById(picture);
        if (!update) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return ResultUtils.success(update);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @ApiOperation("根据id获取图片")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        if (ObjectUtil.isEmpty(id)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(ObjectUtil.isEmpty(picture),ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    @ApiOperation("根据id获取图片vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        if (ObjectUtil.isEmpty(id)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(ObjectUtil.isEmpty(picture),ErrorCode.NOT_FOUND_ERROR);
        PictureVO pictureVO = PictureVO.objToVo(picture);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @ApiOperation("分页获取图片列表")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        Page<Picture> picturePage = pictureService.listPictureByPage(pictureQueryRequest);
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    @ApiOperation("分页获取图片列表list<vo>")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        Page<PictureVO> pictureVOPage = pictureService.listPictureVOByPage(pictureQueryRequest,request);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    @ApiOperation("编辑图片")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (ObjectUtil.isEmpty(pictureEditRequest) || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //指定修改类
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureEditRequest,picture);
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        picture.setEditTime(new Date());
        //图片校验
        pictureService.validPicture(picture);
        ThrowUtils.throwIf(ObjectUtil.isEmpty(pictureService.getById(picture.getId())),ErrorCode.NOT_FOUND_ERROR);
        //编辑
        User loginUser = userService.getLoginUser(request);
        if (loginUser.getUserRole() != UserConstant.ADMIN_ROLE && loginUser.getId() != picture.getUserId()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean update = pictureService.updateById(picture);

        return ResultUtils.success(update);
    }


}
