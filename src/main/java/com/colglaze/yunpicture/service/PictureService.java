package com.colglaze.yunpicture.service;

import com.colglaze.yunpicture.model.dto.picture.PictureUploadRequest;
import com.colglaze.yunpicture.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

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
}
