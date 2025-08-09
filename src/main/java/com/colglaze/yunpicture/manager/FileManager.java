package com.colglaze.yunpicture.manager;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.colglaze.yunpicture.config.CosClientConfig;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
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
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {
                CIObject first = CollUtil.getFirst(objectList);
                CIObject thumbnail = first;
                if (objectList.size() > 1) {
                    thumbnail = objectList.get(1);
                }
                UploadPictureResult pictureResult = UploadPictureResult
                        .builder()
                        .picName(FileUtil.mainName(filename))
                        .picWidth(first.getWidth())
                        .picHeight(first.getHeight())
                        .picScale(NumberUtil.round(first.getWidth() * 1.0 / first.getHeight(), 2).doubleValue())
                        .picFormat(first.getFormat())
                        .picSize(first.getSize().longValue())
                        .url(cosClientConfig.getHost() + "/" + first.getKey())
                        .thumbnailUrl(cosClientConfig.getHost() + "/" + thumbnail.getKey())
                        .build();

                return pictureResult;
            }
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

    /**
     * 根据url上传图片
     * @param fileUrl
     * @param uploadPathPrefix
     * @return
     */
    public UploadPictureResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix) {
//        //校验图片
//        validPicture(fileUrl);
        //图片上传地址(自己拼接防止恶意地址)
        String uuid = RandomUtil.randomString(16);
        String filename = FileUtil.mainName(fileUrl);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(filename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;

        try {
            //创建临时文件
            file = File.createTempFile(uploadPath, null);
            HttpUtil.downloadFile(fileUrl,file);
            //上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {
                CIObject first = CollUtil.getFirst(objectList);
                CIObject thumbnail = first;
                if (objectList.size() > 1) {
                    thumbnail = objectList.get(1);
                }
                UploadPictureResult pictureResult = UploadPictureResult
                        .builder()
                        .picName(FileUtil.mainName(filename))
                        .picWidth(first.getWidth())
                        .picHeight(first.getHeight())
                        .picScale(NumberUtil.round(first.getWidth() * 1.0 / first.getHeight(), 2).doubleValue())
                        .picFormat(first.getFormat())
                        .picSize(first.getSize().longValue())
                        .url(cosClientConfig.getHost() + "/" + first.getKey())
                        .thumbnailUrl(cosClientConfig.getHost() + "/" + thumbnail.getKey())
                        .build();

                return pictureResult;
            }
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

    /**
     * 根据url校验图片
     * @param fileUrl
     */
    public void validPicture(String fileUrl) {
        //校验参数是否为空
        ThrowUtils.throwIf(StrUtil.isEmpty(fileUrl),ErrorCode.PARAMS_ERROR,"文件地址不能为空");
        //校验url格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件格式错误");
        }
        //校验url协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR,"仅支持HTTP 或 HTTPS 协议的文件地址");
        //发送head请求以验证文件是否存在 head请求仅返回响应头的信息，不会下载文件，但是不能用get请求，否则会下载文件
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD,fileUrl).execute();
            //未正常返回，无需执行其他判断
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            //校验文件类型
            String contentType = response.header("Content-Type");
            final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg","image/jpg","image/png","image/webp");
            if (!ALLOW_CONTENT_TYPES.contains(contentType)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件类型错误");
            }
            //校验文件大小
            String contentLength = response.header("Content-Length");
            if (StrUtil.isNotEmpty(contentLength)) {
                final long ONE_M = 1024 * 1024L;
                long size = Long.parseLong(contentLength);
                ThrowUtils.throwIf(size > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小应该小于2M");
                return;
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件格式错误");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (ObjectUtil.isNotEmpty(response)) {
                response.close();
            }
        }


    }

}
