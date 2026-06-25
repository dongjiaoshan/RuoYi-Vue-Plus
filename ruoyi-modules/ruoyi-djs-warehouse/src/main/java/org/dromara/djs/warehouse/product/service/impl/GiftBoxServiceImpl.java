package org.dromara.djs.warehouse.product.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.warehouse.product.domain.GiftBox;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.bo.GiftBoxBo;
import org.dromara.djs.warehouse.product.domain.vo.GiftBoxVo;
import org.dromara.djs.warehouse.product.mapper.GiftBoxMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.service.IGiftBoxService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 礼盒组件 Service 实现（WMS-MD-002）。
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Slf4j
@Service
public class GiftBoxServiceImpl extends DjsBaseServiceImpl<GiftBoxMapper, GiftBox> implements IGiftBoxService {

    private final ProductInfoMapper productInfoMapper;

    public GiftBoxServiceImpl(GiftBoxMapper baseMapper, ProductInfoMapper productInfoMapper) {
        super(baseMapper);
        this.productInfoMapper = productInfoMapper;
    }

    @Override
    public List<GiftBoxVo> queryByBoxId(Long boxProductId) {
        if (boxProductId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<GiftBox> wrapper = new LambdaQueryWrapper<GiftBox>()
            .eq(GiftBox::getBoxProductId, boxProductId)
            .orderByAsc(GiftBox::getComponentSort)
            .orderByAsc(GiftBox::getId);
        List<GiftBoxVo> rows = baseMapper.selectVoList(wrapper);
        if (CollUtil.isEmpty(rows)) {
            return Collections.emptyList();
        }
        // 一次性 batch JOIN 拉组件 SKU 名称（避免 N+1）
        List<Long> componentIds = rows.stream()
            .map(GiftBoxVo::getComponentProductId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> nameMap = new HashMap<>();
        if (!componentIds.isEmpty()) {
            List<ProductInfo> products = productInfoMapper.selectBatchIds(componentIds);
            for (ProductInfo p : products) {
                nameMap.put(p.getId(), p.getProductName());
            }
        }
        rows.forEach(r -> r.setComponentProductName(nameMap.get(r.getComponentProductId())));
        return rows;
    }

    @Override
    public Map<Long, List<GiftBoxVo>> queryByBoxIds(Collection<Long> boxProductIds) {
        Map<Long, List<GiftBoxVo>> result = new HashMap<>();
        if (CollUtil.isEmpty(boxProductIds)) {
            return result;
        }
        for (Long boxId : boxProductIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList())) {
            List<GiftBoxVo> comps = queryByBoxId(boxId);
            if (!comps.isEmpty()) {
                result.put(boxId, comps);
            }
        }
        return result;
    }

    @Override
    public int insertBatch(Long boxProductId, List<GiftBoxBo> components) {
        if (boxProductId == null || CollUtil.isEmpty(components)) {
            return 0;
        }
        List<GiftBox> entities = new ArrayList<>(components.size());
        for (GiftBoxBo bo : components) {
            GiftBox entity = toEntity(bo);
            if (entity == null) {
                continue;
            }
            // 强制覆盖：外层礼盒主体的 id 才是权威，BO 自带的 boxProductId 不可信
            entity.setBoxProductId(boxProductId);
            entity.setId(null);
            entities.add(entity);
        }
        if (entities.isEmpty()) {
            return 0;
        }
        // MyBatis-Plus 没有标准 insertBatch；循环 insert 走 audit 字段填充
        int count = 0;
        for (GiftBox e : entities) {
            count += baseMapper.insert(e);
        }
        return count;
    }

    @Override
    public int softDeleteByBoxId(Long boxProductId) {
        if (boxProductId == null) {
            return 0;
        }
        LambdaQueryWrapper<GiftBox> wrapper = new LambdaQueryWrapper<GiftBox>()
            .eq(GiftBox::getBoxProductId, boxProductId);
        List<GiftBox> existing = baseMapper.selectList(wrapper);
        if (CollUtil.isEmpty(existing)) {
            return 0;
        }
        List<Long> ids = existing.stream().map(GiftBox::getId).collect(Collectors.toList());
        return softDelete(ids);
    }

    @Override
    public int softDeleteByIds(Collection<Long> ids) {
        return softDelete(ids);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus，protected 便于单测覆盖。
     */
    protected GiftBox toEntity(GiftBoxBo bo) {
        return MapstructUtils.convert(bo, GiftBox.class);
    }

}
