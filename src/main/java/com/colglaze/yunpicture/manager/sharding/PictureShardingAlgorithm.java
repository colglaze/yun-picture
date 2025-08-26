package com.colglaze.yunpicture.manager.sharding;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.colglaze.yunpicture.model.entity.Space;
import com.colglaze.yunpicture.model.enums.SpaceLevelEnum;
import com.colglaze.yunpicture.service.SpaceService;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

public class PictureShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> preciseShardingValue) {
        Long spaceId = preciseShardingValue.getValue() == null ? -1L : preciseShardingValue.getValue();
        String logicTableName = preciseShardingValue.getLogicTableName();
        String sql = "SELECT spaceLevel FROM space WHERE id = " + spaceId;
        Integer spaceLevel = (Integer) SqlRunner.db().selectObj(sql);
        boolean equal = ObjUtil.notEqual(spaceLevel, SpaceLevelEnum.FLAGSHIP.getValue());
        // spaceId 为 null 表示查询所有图片
        if (spaceId == null || equal || spaceId == -1L) {
            return logicTableName;
        }
        // 根据 spaceId 动态生成分表名
        String realTableName = "picture_" + spaceId;
        if (availableTargetNames.contains(realTableName)) {
            return realTableName;
        } else {
            return logicTableName;
        }
    }

    @Override
    public Collection<String> doSharding(Collection<String> collection, RangeShardingValue<Long> rangeShardingValue) {
        return new ArrayList<>();
    }

    @Override
    public Properties getProps() {
        return null;
    }

    @Override
    public void init(Properties properties) {

    }
}
