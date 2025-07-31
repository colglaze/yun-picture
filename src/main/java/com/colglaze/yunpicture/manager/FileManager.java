package com.colglaze.yunpicture.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;

import com.colglaze.yunpicture.config.CosClientConfig;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class FileManager {

    private final CosClientConfig cosClientConfig;

    private final CosManager cosManager;

    // ...

    /**
     * @param multipartFile    文件
     * @param uploadPathPrefix 路径前缀
     * @return
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        //校验图片
        validPicture(multipartFile);
        //图片上传地址(自己拼接防止恶意地址)
        String uuid = RandomUtil.randomString(16);
        String filename = multipartFile.getOriginalFilename();
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(filename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;

        try {
            //创建临时文件
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);
            //上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            //封装返回结果
            UploadPictureResult pictureResult = UploadPictureResult
                    .builder()
                    .picName(FileUtil.mainName(filename))
                    .picWidth(imageInfo.getWidth())
                    .picHeight(imageInfo.getHeight())
                    .picScale(NumberUtil.round(imageInfo.getWidth() * 1.0 / imageInfo.getHeight(), 2).doubleValue())
                    .picFormat(imageInfo.getFormat())
                    .picSize(FileUtil.size(file))
                    .url(cosClientConfig.getHost() + "/" + uploadPath)
                    .build();

            return pictureResult;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            this.deleteTempFile(file);
        }

    }

    private void deleteTempFile(File file) {
        if (ObjectUtil.isEmpty(file)) {
            return;
        }
        boolean delete = file.delete();
        if (!delete) {
            log.error("文件删除失败，地址：" + file.getAbsolutePath());
        }
    }

    /**
     * 校验图片
     *
     * @param multipartFile
     */
    public void validPicture(MultipartFile multipartFile) {
        //不能为空
        ThrowUtils.throwIf(ObjectUtil.isEmpty(multipartFile), ErrorCode.PARAMS_ERROR, "文件不能为空");

        //大小 <= 2m
        long size = multipartFile.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(size > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小应该小于2M");
        //校验文件后缀
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        //允许文件上传的后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpg", "jpeg", "webp", "png");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "文件类型错误");

    }


}
