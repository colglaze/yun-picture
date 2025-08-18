package com.colglaze.yunpicture.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.*;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.colglaze.yunpicture.common.BaseResponse;
import com.colglaze.yunpicture.common.DeleteRequest;
import com.colglaze.yunpicture.common.ResultUtils;
import com.colglaze.yunpicture.constant.RedisConstant;
import com.colglaze.yunpicture.constant.UserConstant;
import com.colglaze.yunpicture.exceptions.BusinessException;
import com.colglaze.yunpicture.exceptions.ErrorCode;
import com.colglaze.yunpicture.exceptions.ThrowUtils;
import com.colglaze.yunpicture.manager.AliYunAiManager;
import com.colglaze.yunpicture.manager.FileManager;

import com.colglaze.yunpicture.manager.ImageMetadataManage;
import com.colglaze.yunpicture.model.dto.file.UploadPictureResult;
import com.colglaze.yunpicture.model.dto.picture.*;
import com.colglaze.yunpicture.model.entity.Picture;
import com.colglaze.yunpicture.model.entity.Space;
import com.colglaze.yunpicture.model.entity.User;
import com.colglaze.yunpicture.model.enums.PictureReviewStatusEnum;
import com.colglaze.yunpicture.model.vo.CreateOutPaintingTaskResponse;
import com.colglaze.yunpicture.model.vo.PictureTagCategory;
import com.colglaze.yunpicture.model.vo.PictureVO;
import com.colglaze.yunpicture.model.vo.UserVO;
import com.colglaze.yunpicture.service.PictureService;
import com.colglaze.yunpicture.mapper.PictureMapper;
import com.colglaze.yunpicture.service.SpaceService;
import com.colglaze.yunpicture.service.UserService;
import com.colglaze.yunpicture.utils.CaffeineUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.colglaze.yunpicture.constant.RedisConstant.PICTURE_SINGLE_KEY;
import static com.colglaze.yunpicture.constant.RedisConstant.PICTURE_VERSION_KEY;
import static com.colglaze.yunpicture.constant.RedisConstant.PICTURE_VERSION_USER_PREFIX;
import static com.colglaze.yunpicture.constant.RedisConstant.PICTURE_VERSION_SPACE_PREFIX;
import static com.colglaze.yunpicture.constant.UserConstant.USER_LOGIN_STATE;
import static com.colglaze.yunpicture.utils.CaffeineUtil.LOCAL_CACHE;

/**
 * @author ColorGlaze
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-07-28 16:34:36
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    private final FileManager fileManager;
    private final UserService userService;
    private final ImageMetadataManage imageMetadataService;
    private final PictureMapper pictureMapper;
    private final StringRedisTemplate redisTemplate;
    private final SpaceService spaceService;
    private final TransactionTemplate transactionTemplate;
    private final AliYunAiManager aliYunAiManager;


    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) throws IOException {
        //参数校验
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(pictureUploadRequest, multipartFile), ErrorCode.PARAMS_ERROR);
        String uploadPathPrefix = getUploadPathPrefix(null, multipartFile, pictureUploadRequest, loginUser, false);
        ImageMetadata imageMetadata = imageMetadataService.generateMetadata(multipartFile.getBytes());
        UploadPictureResult pictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        //构造入库信息
        Long spaceId = ObjectUtil.isNotEmpty(pictureUploadRequest.getSpaceId()) ? pictureUploadRequest.getSpaceId() : -1L;
        Picture picture = Picture.builder().userId(loginUser.getId()).spaceId(spaceId).build();
        BeanUtil.copyProperties(imageMetadata, picture, true);
        String tags = JSONUtil.toJsonStr(imageMetadata.getTags());
        picture.setTags(tags);
        return getPictureVO(pictureUploadRequest, loginUser, picture, pictureResult);
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
        if (ObjectUtil.hasEmpty(current, pageSize)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (isDefault) {
            queryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        }

        // 1. 构建分页缓存键（加入版本号，避免全量删除）
        String queryCondition = JSONUtil.toJsonStr(queryRequest);
        String hashKey = DigestUtils.md5Hex(queryCondition.getBytes());
        // 选择更细粒度的版本号（优先按空间，其次用户，最后全局）
        Long spaceIdForVersion = queryRequest.getSpaceId();
        Long userIdForVersion = queryRequest.getUserId();
        long version;
        if (spaceIdForVersion != null) {
            String versionStr = redisTemplate.opsForValue().get(PICTURE_VERSION_SPACE_PREFIX + spaceIdForVersion);
            version = defaultZero(StrUtil.isNotEmpty(versionStr) ? Long.valueOf(versionStr) : null);
        } else if (userIdForVersion != null) {
            String versionStr = redisTemplate.opsForValue().get(PICTURE_VERSION_USER_PREFIX + userIdForVersion);
            version = defaultZero(StrUtil.isNotEmpty(versionStr) ? Long.valueOf(versionStr) : null);
        } else {
            String versionStr = redisTemplate.opsForValue().get(PICTURE_VERSION_KEY);
            version = defaultZero(StrUtil.isNotEmpty(versionStr) ? Long.valueOf(versionStr) : null);
        }
        String pageCacheKey = RedisConstant.LIST_PICTURE_BY_PAGE_INDEX + hashKey + ":v" + version;

        // 2. 本地缓存查询（设置过期时间，与Redis协同）
        String cachedPageIds = LOCAL_CACHE.getIfPresent(pageCacheKey);
        if (StrUtil.isNotEmpty(cachedPageIds)) {
            // 从缓存的ID列表加载完整数据（依赖单条缓存）
            return buildPageFromIds(cachedPageIds, current, pageSize);
        }

        // 3. Redis缓存查询
        String redisPageIds = redisTemplate.opsForValue().get(pageCacheKey);
        if (StrUtil.isNotEmpty(redisPageIds)) {
            // 回写本地缓存（设置随机过期，短于Redis）
            LOCAL_CACHE.put(pageCacheKey, redisPageIds); // 5-7分钟
            return buildPageFromIds(redisPageIds, current, pageSize);
        }

        // 4. 数据库查询
        IPage<Long> idPage = listPictureIdsByPage(new Page<>(current, pageSize), queryRequest, isDefault);

        List<Long> idList = idPage.getRecords();
        long total = idPage.getTotal();
        
        // 构建包含总数和ID列表的缓存对象
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("ids", idList);
        cacheData.put("total", total);
        String cacheJson = JSONUtil.toJsonStr(cacheData);
        
        // 5. 缓存数据（Redis设置10-15分钟随机过期）
        int cacheExpireTime = 600 + RandomUtil.randomInt(0, 300);
        redisTemplate.opsForValue().set(pageCacheKey, cacheJson, cacheExpireTime, TimeUnit.SECONDS);
        // 本地缓存设置较短过期（5-7分钟）
        LOCAL_CACHE.put(pageCacheKey, cacheJson);

        // 6. 构建完整分页结果
        return buildPageFromIds(cacheJson, current, pageSize, total);
    }

    public IPage<Long> listPictureIdsByPage(IPage<Long> page, PictureQueryRequest queryRequest, boolean isDefault) {
        // 构建完整的查询条件
        LambdaQueryWrapper<Picture> wrapper = buildQueryWrapper(queryRequest, isDefault);
        
        // 先查询总数（不限制字段，确保总数计算正确）
        long total = this.count(wrapper);
        
        // 再查询当前页的ID列表
        wrapper.select(Picture::getId);
        wrapper.orderByDesc(Picture::getCreateTime);
        Page<Picture> picturePage = this.page(new Page<>(page.getCurrent(), page.getSize()), wrapper);
        List<Long> idList = picturePage.getRecords().stream()
                .map(Picture::getId)
                .collect(Collectors.toList());

        // 手动组装 IPage<Long>，使用正确的总数
        Page<Long> idPage = new Page<>(page.getCurrent(), page.getSize(), total);
        idPage.setRecords(idList);
        return idPage;
    }

    /**
     * 根据ID列表构建完整分页结果（依赖单条数据缓存）
     */
    private Page<Picture> buildPageFromIds(String cacheJson, int current, int pageSize) {
        return buildPageFromIds(cacheJson, current, pageSize, null);
    }

    private Page<Picture> buildPageFromIds(String cacheJson, int current, int pageSize, Long total) {
        // 解析缓存数据
        Map<String, Object> cacheData = JSONUtil.toBean(cacheJson,
                new TypeReference<Map<String, Object>>() {}, true);
        List<Long> idList = JSONUtil.toList(JSONUtil.toJsonStr(cacheData.get("ids")), Long.class);
        
        // 安全地处理 total 字段的类型转换
        Object totalObj = cacheData.get("total");
        Long cachedTotal = null;
        if (totalObj instanceof Integer) {
            cachedTotal = ((Integer) totalObj).longValue();
        } else if (totalObj instanceof Long) {
            cachedTotal = (Long) totalObj;
        } else if (totalObj instanceof Number) {
            cachedTotal = ((Number) totalObj).longValue();
        }
        
        if (CollUtil.isEmpty(idList)) {
            return new Page<>(current, pageSize, cachedTotal != null ? cachedTotal : 0);
        }

        // 1) 优先从本地缓存读取，记录缺失的 ID
        Map<Long, Picture> idToPicture = new java.util.HashMap<>();
        List<Long> missingIds = new java.util.ArrayList<>();
        for (Long id : idList) {
            String singleKey = PICTURE_SINGLE_KEY + id;
            String cachedLocalJson = CaffeineUtil.LOCAL_CACHE.getIfPresent(singleKey);
            if (StrUtil.isNotEmpty(cachedLocalJson)) {
                Picture cached = JSONUtil.toBean(cachedLocalJson, Picture.class);
                if (cached != null) {
                    idToPicture.put(id, cached);
                    continue;
                }
            }
            missingIds.add(id);
        }

        // 2) 通过 Redis 批量获取剩余数据
        if (CollUtil.isNotEmpty(missingIds)) {
            List<String> redisKeys = missingIds.stream()
                    .map(id -> PICTURE_SINGLE_KEY + id)
                    .collect(Collectors.toList());
            List<String> redisValues = redisTemplate.opsForValue().multiGet(redisKeys);
            if (CollUtil.isNotEmpty(redisValues)) {
                for (int i = 0; i < redisKeys.size(); i++) {
                    String value = redisValues.get(i);
                    if (StrUtil.isNotEmpty(value)) {
                        Long id = missingIds.get(i);
                        Picture cached = JSONUtil.toBean(value, Picture.class);
                        if (cached != null) {
                            idToPicture.put(id, cached);
                            // 回写本地缓存
                            CaffeineUtil.LOCAL_CACHE.put(PICTURE_SINGLE_KEY + id, value);
                        }
                    }
                }
            }

            // 3) 仍缺失的，批量查数据库并回填缓存
            List<Long> stillMissing = missingIds.stream()
                    .filter(id -> !idToPicture.containsKey(id))
                    .collect(Collectors.toList());
            if (CollUtil.isNotEmpty(stillMissing)) {
                List<Picture> dbList = this.listByIds(stillMissing);
                if (CollUtil.isNotEmpty(dbList)) {
                    for (Picture p : dbList) {
                        idToPicture.put(p.getId(), p);
                        String json = JSONUtil.toJsonStr(p);
                        String key = PICTURE_SINGLE_KEY + p.getId();
                        // Redis 缓存 2 小时
                        redisTemplate.opsForValue().set(key, json, 2, TimeUnit.HOURS);
                        // 本地缓存
                        CaffeineUtil.LOCAL_CACHE.put(key, json);
                    }
                }
            }
        }

        // 维持与 id 顺序一致的结果列表
        List<Picture> pictureList = idList.stream()
                .map(idToPicture::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 优先使用传入的 total，其次使用缓存中的 total
        long totalCount = total != null ? total : (cachedTotal != null ? cachedTotal : idList.size());
        
        Page<Picture> page = new Page<>(current, pageSize, totalCount);
        page.setRecords(pictureList);
        return page;
    }

    /**
     * 单条图片数据查询（多级缓存）
     */
    private Picture getPictureById(Long id) {
        String singleKey = PICTURE_SINGLE_KEY + id;
        // 1. 查本地缓存（先取字符串再反序列化，避免 null 参与反序列化导致 NPE）
        String localJson = LOCAL_CACHE.getIfPresent(singleKey);
        if (StrUtil.isNotEmpty(localJson)) {
            Picture picture = JSONUtil.toBean(localJson, Picture.class);
            if (picture != null) {
                return picture;
            }
        }
        // 2. 查Redis
        String pictureJson = redisTemplate.opsForValue().get(singleKey);
        if (StrUtil.isNotEmpty(pictureJson)) {
            Picture cachedPicture = JSONUtil.toBean(pictureJson, Picture.class);
            LOCAL_CACHE.put(singleKey, pictureJson); // 本地缓存10分钟
            return cachedPicture;
        }
        // 3. 查数据库
        Picture dbPicture = this.getById(id);
        if (dbPicture != null) {
            // 存入Redis（2小时过期）
            redisTemplate.opsForValue().set(singleKey, JSONUtil.toJsonStr(dbPicture), 2, TimeUnit.HOURS);
            LOCAL_CACHE.put(singleKey, JSONUtil.toJsonStr(dbPicture));
        }
        return dbPicture;
    }

    private void cacheSingle(Picture picture) {
        if (picture == null || picture.getId() == null) {
            return;
        }
        String key = PICTURE_SINGLE_KEY + picture.getId();
        String json = JSONUtil.toJsonStr(picture);
        // Redis 2 小时
        redisTemplate.opsForValue().set(key, json, 2, TimeUnit.HOURS);
        // 本地缓存（同样写入，生命周期由 Caffeine 控制）
        LOCAL_CACHE.put(key, json);
    }

    private void evictSingle(Long id) {
        if (id == null) return;
        String key = PICTURE_SINGLE_KEY + id;
        LOCAL_CACHE.invalidate(key);
        redisTemplate.delete(key);
    }

    /**
     * 构建查询条件（提取为独立方法）
     */
    private LambdaQueryWrapper<Picture> buildQueryWrapper(PictureQueryRequest queryRequest, boolean isDefault) {
        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ObjectUtil.isNotEmpty(queryRequest.getId()), Picture::getId, queryRequest.getId())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getUserId()), Picture::getUserId, queryRequest.getUserId())
                .like(StrUtil.isNotBlank(queryRequest.getName()), Picture::getName, queryRequest.getName())
                .like(StrUtil.isNotBlank(queryRequest.getIntroduction()), Picture::getIntroduction, queryRequest.getIntroduction())
                .like(StrUtil.isNotBlank(queryRequest.getPicFormat()), Picture::getPicFormat, queryRequest.getPicFormat())
                .like(StrUtil.isNotBlank(queryRequest.getCategory()), Picture::getCategory, queryRequest.getCategory())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicWidth()), Picture::getPicWidth, queryRequest.getPicWidth())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicHeight()), Picture::getPicHeight, queryRequest.getPicHeight())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicSize()), Picture::getPicSize, queryRequest.getPicSize())
                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicScale()), Picture::getPicScale, queryRequest.getPicScale())
                .eq(isDefault || ObjectUtil.isNotEmpty(queryRequest.getReviewStatus()),
                        Picture::getReviewStatus, queryRequest.getReviewStatus());
        if (StrUtil.isNotBlank(queryRequest.getSearchText())) {
            queryWrapper.and(qw -> qw.like(Picture::getIntroduction, queryRequest.getSearchText())
                    .or().like(Picture::getName, queryRequest.getSearchText()));
        }
        if (ArrayUtil.isNotEmpty(queryRequest.getTags())) {
            for (String tag : queryRequest.getTags()) {
                queryWrapper.like(Picture::getTags, tag);
            }
        }
        if (ObjectUtil.isNotEmpty(queryRequest.getSpaceId())) {
            queryWrapper.eq(Picture::getSpaceId,queryRequest.getSpaceId());
        } else {
            queryWrapper.eq(Picture::getSpaceId, -1L);
        }
        queryWrapper.orderByDesc(Picture::getCreateTime);
        return queryWrapper;
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
        if (picture.getSpaceId() != -1L) {
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("私人空间无需审核");
            picture.setReviewTime(LocalDateTime.now());
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        }
    }

    @Override
    public PictureVO uploadPicture(String fileUrl, PictureUploadRequest pictureUploadRequest, User loginUser) {
        //参数校验
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(pictureUploadRequest, fileUrl), ErrorCode.PARAMS_ERROR);
        String uploadPathPrefix = getUploadPathPrefix(fileUrl, null, pictureUploadRequest, loginUser, true);
        //ai填充信息
        ImageMetadata imageMetadata = null;
        try {
            imageMetadata = imageMetadataService.generateMetadata(downloadImageFromUrl(fileUrl));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件传输错误");
        }
        UploadPictureResult pictureResult = fileManager.uploadPictureByUrl(fileUrl, uploadPathPrefix);
        //构造入库信息
        // 构造要入库的图片信息
        Long spaceId = ObjectUtil.isNotEmpty(pictureUploadRequest.getSpaceId()) ? pictureUploadRequest.getSpaceId() : -1L;
        Picture picture = Picture.builder().userId(loginUser.getId()).spaceId(spaceId).build();
        BeanUtil.copyProperties(imageMetadata, picture);
        picture.setTags(JSONUtil.toJsonStr(imageMetadata.getTags()));
        return getPictureVO(pictureUploadRequest, loginUser, picture, pictureResult);
    }



    @Override
    public int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
            if (StrUtil.isEmpty(namePrefix)) {
                namePrefix = searchText;
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
//            if (StrUtil.isNotBlank(namePrefix)) {
//                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
//            }
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    @Override
    public PictureTagCategory getCateAndTags() {
        //查缓存
        String cacheValue = redisTemplate.opsForValue().get(RedisConstant.GET_CATE_AND_TAGS);
        //命中，返回
        if (StrUtil.isNotEmpty(cacheValue)) {
            PictureTagCategory tagCategory = JSONUtil.toBean(cacheValue, PictureTagCategory.class);
            return tagCategory;
        }
        List<String> tags = pictureMapper.getTags();
        List<String> category = pictureMapper.getCategory();
        PictureTagCategory tagCategory = new PictureTagCategory(tags, category);
        cacheValue = JSONUtil.toJsonStr(tagCategory);
        //写缓存
        redisTemplate.opsForValue().set(RedisConstant.GET_CATE_AND_TAGS, cacheValue, 1, TimeUnit.DAYS);
        return tagCategory;
    }

    @Override
    public BaseResponse<Boolean> deletePicture(DeleteRequest deleteRequest, HttpServletRequest request) {
        //只有管理员和本人才可以删除照片
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(deleteRequest, loginUser), ErrorCode.PARAMS_ERROR, "用户未登录或参数为空");
        Picture picture = this.getById(deleteRequest.getId());
        ThrowUtils.throwIf(ObjectUtil.isEmpty(picture), ErrorCode.NOT_FOUND_ERROR);
        checkPictureAuth(loginUser, picture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放额度
            Long spaceId = picture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + picture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });

        // 单条缓存剔除
        evictSingle(picture.getId());
        // 刷新版本：全局 + 用户/空间维度
        incrementVersion(loginUser.getId(), picture.getSpaceId());
        return ResultUtils.success(true);
    }


    private PictureVO getPictureVO(PictureUploadRequest pictureUploadRequest, User loginUser,
                                   Picture picture, UploadPictureResult pictureResult) {
        this.fillReviewParams(picture, loginUser);
        BeanUtil.copyProperties(pictureResult, picture, true);
        //pictureId不为空，更新，补充id和编辑时间
        Long pictureId = pictureUploadRequest.getId();
        if (ObjectUtil.isNotEmpty(pictureId)) {
            //权限校验
            if (ObjectUtil.equal(picture.getUserId(), loginUser.getId()) ||
                    StrUtil.equals(loginUser.getUserRole(), UserConstant.ADMIN_ROLE)) {
                picture.setId(pictureId);
                picture.setEditTime(LocalDateTime.now());
            } else {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只有本人和管理员才可以编辑图片");
            }

        }
        //否则直接入库
//        picture.setSpaceId(pictureUploadRequest.getSpaceId());
        // 开启事务
        Long finalSpaceId = ObjectUtil.isNotEmpty(pictureUploadRequest.getSpaceId()) ? pictureUploadRequest.getSpaceId() : -1L;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != -1) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
        // 写入单条缓存，便于后续详情/拼装命中
        cacheSingle(picture);
        // 刷新版本：全局 + 用户/空间维度
        incrementVersion(loginUser.getId(), picture.getSpaceId());
        return PictureVO.objToVo(picture);
    }


    //上传图片之前的校验
    private String getUploadPathPrefix(String fileUrl, MultipartFile multipartFile,
                                       PictureUploadRequest pictureUploadRequest,
                                       User loginUser, boolean isUrl) {

        //校验用户是否登录
        ThrowUtils.throwIf(ObjectUtil.isEmpty(loginUser), ErrorCode.NOT_LOGIN_ERROR);
        // 空间权限校验
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null && spaceId != -1L) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 必须空间创建人（管理员）才能上传
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        Long pictureId = pictureUploadRequest.getId();
        // 如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !StrUtil.equals(UserConstant.ADMIN_ROLE, loginUser.getUserRole())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原有图片一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        //上传图片，得到信息
        //按照用户id划分目录
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        //校验图片
        if (isUrl) {
            fileManager.validPicture(fileUrl);
        } else {
            fileManager.validPicture(multipartFile);
        }
        return uploadPathPrefix;
    }
    /**
     * 从 URL 下载图片，转换为字节数组
     *
     * @param imageUrl 图片的 URL 地址
     * @return 图片字节数组
     * @throws IOException 下载过程中 IO 异常
     */
    private byte[] downloadImageFromUrl(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        URLConnection connection = url.openConnection();
        // 可设置超时时间（可选）
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        // 读取流并转换为字节数组
        try (var inputStream = connection.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    /**
     * 递增全局与用户维度的版本号
     */
    private void incrementVersionForUser(Long userId) {
        try {
            redisTemplate.opsForValue().increment(PICTURE_VERSION_KEY, 1);
            if (userId != null) {
                redisTemplate.opsForValue().increment(PICTURE_VERSION_USER_PREFIX + userId, 1);
            }
        } catch (Exception ignore) {
            // 忽略递增失败，不影响主流程
        }
    }

    /**
     * 递增全局 + 用户/空间维度版本
     */
    private void incrementVersion(Long userId, Long spaceId) {
        try {
            redisTemplate.opsForValue().increment(PICTURE_VERSION_KEY, 1);
            if (userId != null) {
                redisTemplate.opsForValue().increment(PICTURE_VERSION_USER_PREFIX + userId, 1);
            }
            if (spaceId != null) {
                redisTemplate.opsForValue().increment(PICTURE_VERSION_SPACE_PREFIX + spaceId, 1);
            }
        } catch (Exception ignore) {
            // 忽略递增失败
        }
    }

    /**
     * 将 null 视为 0
     */
    private long defaultZero(Long value) {
        return value == null ? 0L : value;
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = ObjectUtil.isNotEmpty(picture.getSpaceId()) ? picture.getSpaceId() : -1L;
        if (spaceId == -1L) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) &&
                    !StrUtil.equals(UserConstant.ADMIN_ROLE, loginUser.getUserRole())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Override
    public BaseResponse<Boolean> updatePicture(PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(ObjectUtil.isEmpty(pictureUpdateRequest), ErrorCode.PARAMS_ERROR);
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureUpdateRequest, picture);
        String tags = JSONUtil.toJsonStr(pictureUpdateRequest.getTags());
        picture.setTags(tags);
        //判断是否存在
        if (ObjectUtil.isEmpty(this.getById(picture.getId()))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片不存在");
        }
        //校验图片信息
        User loginUser = userService.getLoginUser(request);
        this.validPicture(picture);
        this.fillReviewParams(picture, loginUser);
//        this.checkPictureAuth(loginUser, picture);
        boolean update = this.updateById(picture);
        if (!update) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        // 回写单条缓存（使用最新数据）
        Picture latest = this.getById(picture.getId());
        if (latest != null) {
            cacheSingle(latest);
        } else {
            evictSingle(picture.getId());
        }
        // 刷新版本：全局 + 用户/空间维度
        incrementVersion(loginUser.getId(), picture.getSpaceId());
        return ResultUtils.success(update);
    }

    @Override
    public BaseResponse<Boolean> editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (ObjectUtil.isEmpty(pictureEditRequest) || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //指定修改类
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureEditRequest, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        picture.setEditTime(LocalDateTime.now());
        //图片校验
        this.validPicture(picture);
        ThrowUtils.throwIf(ObjectUtil.isEmpty(this.getById(picture.getId())), ErrorCode.NOT_FOUND_ERROR);
        picture.setUserId(this.getById(picture.getId()).getUserId());
        //编辑
        User loginUser = userService.getLoginUser(request);
        if (StrUtil.equals(loginUser.getUserRole(), UserConstant.ADMIN_ROLE) || ObjectUtil.equal(loginUser.getId(), picture.getUserId())) {
            checkPictureAuth(loginUser, picture);
            if (ObjectUtil.isEmpty(picture.getSpaceId())) {
                picture.setSpaceId(-1L);
            }
            this.fillReviewParams(picture, loginUser);
            boolean update = this.updateById(picture);
            if (update) {
                Picture latest = this.getById(picture.getId());
                if (latest != null) {
                    cacheSingle(latest);
                } else {
                    evictSingle(picture.getId());
                }
            }
            // 刷新版本：全局 + 用户/空间维度
            incrementVersion(loginUser.getId(), picture.getSpaceId());
            return ResultUtils.success(update);
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR);

    }

    @Override
    public Picture getPictureById(long id, HttpServletRequest request) {
        if (ObjectUtil.isEmpty(id)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 查询数据库
        Picture picture = this.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        if (ObjectUtil.isNotEmpty(spaceId)) {
            User loginUser = userService.getLoginUser(request);
            this.checkPictureAuth(loginUser, picture);
        }
        ThrowUtils.throwIf(ObjectUtil.isEmpty(picture), ErrorCode.NOT_FOUND_ERROR);
        return picture;
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
                                                                      User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 权限校验
        checkPictureAuth(loginUser, picture);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        // 创建任务
        return aliYunAiManager.createOutPaintingTask(taskRequest);
    }


    /**
     * 获取当前版本号（调试用）
     */
    public Map<String, Long> getCurrentVersions(Long userId, Long spaceId) {
        Map<String, Long> versions = new HashMap<>();
        String globalVersionStr = redisTemplate.opsForValue().get(PICTURE_VERSION_KEY);
        versions.put("global", defaultZero(StrUtil.isNotEmpty(globalVersionStr) ? Long.valueOf(globalVersionStr) : null));
        
        if (userId != null) {
            String userVersionStr = redisTemplate.opsForValue().get(PICTURE_VERSION_USER_PREFIX + userId);
            versions.put("user_" + userId, defaultZero(StrUtil.isNotEmpty(userVersionStr) ? Long.valueOf(userVersionStr) : null));
        }
        
        if (spaceId != null) {
            String spaceVersionStr = redisTemplate.opsForValue().get(PICTURE_VERSION_SPACE_PREFIX + spaceId);
            versions.put("space_" + spaceId, defaultZero(StrUtil.isNotEmpty(spaceVersionStr) ? Long.valueOf(spaceVersionStr) : null));
        }
        
        return versions;
    }

}


//    public Page<Picture> listPictureByPage(PictureQueryRequest queryRequest, boolean isDefault) {
//        int current = queryRequest.getCurrent();
//        int pageSize = queryRequest.getPageSize();
//        //参数校验
//        if (ObjectUtil.hasEmpty(current, pageSize)) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR);
//        }
//        //审核条件构建
//        if (isDefault) {
//            queryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
//        }
//        // 构建缓存 key
//        String queryCondition = JSONUtil.toJsonStr(queryRequest);
//        String hashKey = DigestUtils.md5Hex(queryCondition.getBytes());
//        String cacheKey = RedisConstant.LIST_PICTURE_BY_PAGE_INDEX + hashKey;
//        // 从本地缓存中查询
//        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
//        if (StrUtil.isNotEmpty(cachedValue)) {
//            // 如果缓存命中，返回结果
//            Page<Picture> cachedPage = JSONUtil.toBean(
//                    cachedValue,
//                    new TypeReference<Page<Picture>>() {
//                    },  // 明确指定泛型类型
//                    true  // 忽略未知属性，避免实体类字段变化导致反序列化失败
//            );
//            return cachedPage;
//        }
//
//        //从redis中查询
//        String cacheValue = redisTemplate.opsForValue().get(cacheKey);
//        if (StrUtil.isNotEmpty(cacheValue)) {
//            //如果命中，返回结果
//            Page<Picture> cachePage = JSONUtil.toBean(
//                    cacheValue,
//                    new TypeReference<Page<Picture>>() {
//                    },  // 明确指定泛型类型
//                    true  // 忽略未知属性，避免实体类字段变化导致反序列化失败
//            );
//            return cachePage;
//        }
//        //构建查询条件
//        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
//        queryWrapper.eq(ObjectUtil.isNotEmpty(queryRequest.getId()), Picture::getId, queryRequest.getId())
//                .eq(ObjectUtil.isNotEmpty(queryRequest.getUserId()), Picture::getUserId, queryRequest.getUserId())
//                .like(StrUtil.isNotBlank(queryRequest.getName()), Picture::getName, queryRequest.getName())
//                .like(StrUtil.isNotBlank(queryRequest.getIntroduction()), Picture::getIntroduction, queryRequest.getIntroduction())
//                .like(StrUtil.isNotBlank(queryRequest.getPicFormat()), Picture::getPicFormat, queryRequest.getPicFormat())
//                .like(StrUtil.isNotBlank(queryRequest.getCategory()), Picture::getCategory, queryRequest.getCategory())
//                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicWidth()), Picture::getPicWidth, queryRequest.getPicWidth())
//                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicHeight()), Picture::getPicHeight, queryRequest.getPicHeight())
//                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicSize()), Picture::getPicSize, queryRequest.getPicSize())
//                .eq(ObjectUtil.isNotEmpty(queryRequest.getPicScale()), Picture::getPicScale, queryRequest.getPicScale())
//                .eq(isDefault || ObjectUtil.isNotEmpty(queryRequest.getReviewStatus())
//                        , Picture::getReviewStatus, queryRequest.getReviewStatus());
//        if (StrUtil.isNotBlank(queryRequest.getSearchText())) {
//            queryWrapper.and(qw -> {
//                qw.like(Picture::getIntroduction, queryRequest.getIntroduction())
//                        .or()
//                        .like(Picture::getName, queryRequest.getSearchText());
//            });
//        }
//        if (ArrayUtil.isNotEmpty(queryRequest.getTags())) {
//            for (String tag : queryRequest.getTags()) {
//                queryWrapper.like(Picture::getTags, tag);
//            }
//        }
//        //排序
//        queryWrapper.orderBy(true, false, Picture::getCreateTime);
//        Page<Picture> page = this.page(new Page<>(current, pageSize), queryWrapper);
//        //存入redis缓存，以及本地缓存
//        cacheValue = JSONUtil.toJsonStr(page);
//        //设置5-10分钟随机过期，防止缓存雪崩（大量数据同一时间过期，大量请求同时访问数据库）
//        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
//        redisTemplate.opsForValue().set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
//        LOCAL_CACHE.put(cacheKey, cacheValue);
//        //将所有页面查询的hashKye加入前缀索引，方便后续刷新
//        redisTemplate.opsForSet().add(RedisConstant.LIST_PICTURE_BY_PAGE_INDEX, cacheKey);
//        redisTemplate.expire(RedisConstant.LIST_PICTURE_BY_PAGE_INDEX, cacheExpireTime + 60, TimeUnit.SECONDS);
//        return page;
//    }

