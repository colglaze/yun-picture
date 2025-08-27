package com.colglaze.yunpicture.controller;


import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colglaze.yunpicture.annotation.AuthCheck;
import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.DeleteRequest;
import com.colglaze.yunpicture.common.ResultUtils;

import com.colglaze.yunpicture.constant.UserConstant;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.manager.picture.AliYunAiManager;
import com.colglaze.yunpicture.manager.picture.CosManager;
import com.colglaze.yunpicture.manager.auth.SpaceUserAuthManager;
import com.colglaze.yunpicture.manager.auth.StpKit;
import com.colglaze.yunpicture.manager.auth.annotation.SaSpaceCheckPermission;
import com.colglaze.yunpicture.manager.auth.constant.SpaceUserPermissionConstant;
import com.colglaze.yunpicture.model.dto.picture.*;
import com.colglaze.yunpicture.model.entity.Picture;
import com.colglaze.yunpicture.model.entity.Space;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.vo.CreateOutPaintingTaskResponse;
import com.colglaze.yunpicture.model.vo.GetOutPaintingTaskResponse;
import com.colglaze.yunpicture.model.vo.PictureTagCategory;
import com.colglaze.yunpicture.model.vo.PictureVO;
import com.colglaze.yunpicture.service.PictureService;
import com.colglaze.yunpicture.service.SpaceService;
import com.colglaze.yunpicture.service.UserService;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.colglaze.yunpicture.constant.UserConstant.USER_LOGIN_STATE;

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
    private final AliYunAiManager aliYunAiManager;
    private final SpaceService spaceService;
    private final SpaceUserAuthManager spaceUserAuthManager;

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
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            @ModelAttribute PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) throws IOException {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        // 由服务层递增更细粒度版本，无需在 Controller 重复递增
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     */
    @PostMapping("/delete")
    @ApiOperation("删除图片")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        return pictureService.deletePicture(deleteRequest, request);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @ApiOperation("更新图片")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        return pictureService.updatePicture(pictureUpdateRequest, request);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @ApiOperation("根据id获取图片")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        return ResultUtils.success(pictureService.getPictureById(id, request));
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    @ApiOperation("根据id获取图片vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        Picture picture = pictureService.getPictureById(id, request);
        Space space = null;
        Long spaceId = picture.getSpaceId() == -1L ? null : picture.getSpaceId();
        if (ObjUtil.isNotEmpty(spaceId)) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(ObjUtil.isEmpty(space), ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        User user = (User) request.getSession().getAttribute(USER_LOGIN_STATE);
        User loginUser = null;
        //判断是否登录
        if (ObjectUtil.isNotEmpty(user)) {
            loginUser = userService.getLoginUser(request);
        }
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        PictureVO pictureVO = PictureVO.objToVo(picture);
        pictureVO.setPermissionList(permissionList);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @ApiOperation("分页获取图片列表")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {

        Page<Picture> picturePage = pictureService.listPictureByPage(pictureQueryRequest, false);
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    @ApiOperation("分页获取图片列表list<vo>")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        Page<PictureVO> pictureVOPage = pictureService.listPictureVOByPage(pictureQueryRequest, request);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    @ApiOperation("编辑图片")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        return pictureService.editPicture(pictureEditRequest, request);
    }

    /**
     * 获取标签以及分类
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = pictureService.getCateAndTags();
        return ResultUtils.success(pictureTagCategory);
    }

    @PostMapping("/review")
    @ApiOperation("审核图片")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @ApiOperation("通过url上传图片")
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        // 由服务层递增更细粒度版本，无需在 Controller 重复递增
        return ResultUtils.success(pictureVO);
    }

    /**
     * 批量上传图片
     * @param pictureUploadByBatchRequest
     * @param request
     * @return
     */
    @PostMapping("/upload/batch")
    @ApiOperation("批量上传图片")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        // 由服务层递增更细粒度版本，无需在 Controller 重复递增
        return ResultUtils.success(uploadCount);
    }

    @GetMapping("/debug/versions")
    @ApiOperation("获取当前版本号（调试用）")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Long>> getCurrentVersions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long spaceId) {
        Map<String, Long> versions = pictureService.getCurrentVersions(userId, spaceId);
        return ResultUtils.success(versions);
    }

    /**
     * 创建 AI 扩图任务
     */
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunAiManager.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }


}
