package com.colglaze.yunpicture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.DeleteRequest;
import com.colglaze.yunpicture.model.dto.picture.*;
import com.colglaze.yunpicture.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.vo.PictureTagCategory;
import com.colglaze.yunpicture.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

/**
* @author ColorGlaze
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-07-28 16:34:36
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) throws IOException;

    /**
     * 校验图片
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 查询图片列表
     *
     * @param pictureQueryRequest
     * @param isDefault
     * @return
     */
    Page<Picture> listPictureByPage(PictureQueryRequest pictureQueryRequest, boolean isDefault);

    /**
     * 用户分页查询图片列表
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    Page<PictureVO> listPictureVOByPage(PictureQueryRequest pictureQueryRequest, HttpServletRequest request);

    /**
     * 审核图片功能
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 自动填充审核参数
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture,User loginUser);

    /**
     * 通过url上传图片
     * @param fileUrl
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(String fileUrl, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 批量上传图片
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return
     */
    int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    /**
     * 获取标签和分类
     * @return
     */
    PictureTagCategory getCateAndTags();

    /**
     * 删除图片
     * @param deleteRequest
     * @param request
     * @return
     */
    BaseResponse<Boolean> deletePicture(DeleteRequest deleteRequest, HttpServletRequest request);

    /**
     * 校验权限
     * @param loginUser
     * @param picture
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 更新图片
     * @param pictureUpdateRequest
     * @param request
     * @return
     */
    BaseResponse<Boolean> updatePicture(PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request);

    /**
     * 编辑图片
     * @param pictureEditRequest
     * @param request
     * @return
     */
    BaseResponse<Boolean> editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request);

    /**
     * 根据id获取图片详情
     * @param id
     * @param request
     * @return
     */
    Picture getPictureById(long id, HttpServletRequest request);

    /**
     * 获取当前版本号（调试用）
     * @param userId
     * @param spaceId
     * @return
     */
    Map<String, Long> getCurrentVersions(Long userId, Long spaceId);

}
