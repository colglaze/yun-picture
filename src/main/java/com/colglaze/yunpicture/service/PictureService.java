package com.colglaze.yunpicture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colglaze.yunpicture.model.dto.picture.PictureQueryRequest;
import com.colglaze.yunpicture.model.dto.picture.PictureReviewRequest;
import com.colglaze.yunpicture.model.dto.picture.PictureUploadRequest;
import com.colglaze.yunpicture.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

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
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser);

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
}
