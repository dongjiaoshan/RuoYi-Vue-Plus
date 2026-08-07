package org.dromara.djs.plant.crop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.plant.crop.domain.CropProduct;
import org.dromara.djs.plant.crop.domain.bo.CropProductBo;
import org.dromara.djs.plant.crop.domain.vo.CropProductVo;
import org.dromara.djs.plant.crop.mapper.CropProductMapper;
import org.dromara.djs.plant.crop.service.ICropProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 作物关联产品配置 Service 实现（V6 row16）。
 *
 * @author djs
 */
@Slf4j
@Service
public class CropProductServiceImpl extends DjsBaseServiceImpl<CropProductMapper, CropProduct>
    implements ICropProductService {

    public CropProductServiceImpl(CropProductMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    public List<CropProductVo> listByCrop(Long cropId) {
        if (cropId == null) {
            return List.of();
        }
        return baseMapper.selectByCropIds(List.of(cropId));
    }

    @Override
    public Map<Long, List<CropProductVo>> mapByCropIds(Collection<Long> cropIds) {
        if (cropIds == null || cropIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = cropIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        // LinkedHashMap + groupingBy 保持 mapper 的 (crop_id, id) 升序 —— 各作物列表首行即「首选产品」
        return baseMapper.selectByCropIds(ids).stream()
            .collect(Collectors.groupingBy(CropProductVo::getCropId, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(CropProductBo bo) {
        assertProductExists(bo.getProductId());
        assertNotDuplicated(bo.getCropId(), bo.getProductId(), null);
        CropProduct row = new CropProduct();
        row.setCropId(bo.getCropId());
        row.setProductId(bo.getProductId());
        row.setPerfPrice(bo.getPerfPrice());
        row.setDelUnique(0L);
        return baseMapper.insert(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(CropProductBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("缺少配置行 ID", 400);
        }
        CropProduct exist = baseMapper.selectById(bo.getId());
        if (exist == null) {
            throw new ServiceException("产品配置不存在或已删除：" + bo.getId(), 404);
        }
        assertProductExists(bo.getProductId());
        assertNotDuplicated(exist.getCropId(), bo.getProductId(), bo.getId());
        CropProduct row = new CropProduct();
        row.setId(bo.getId());
        row.setProductId(bo.getProductId());
        row.setPerfPrice(bo.getPerfPrice());
        return baseMapper.updateById(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Collection<Long> ids) {
        return softDelete(ids);
    }

    /**
     * 产品必须真实存在且未删。
     *
     * <p>不校验的话可以配出一行指向已删产品的记录：列表 SQL 带 product JOIN 会把它藏掉，
     * 运营既看不见也删不掉，它却仍躺在表里参与结算取价 —— 幽灵配置行。</p>
     */
    private void assertProductExists(Long productId) {
        // 走 mapper 原生 SQL 而不是 ProductInfoMapper —— plant 模块不依赖 warehouse（反向依赖）
        if (productId == null || baseMapper.countLiveProduct(productId) == 0) {
            throw new ServiceException("关联产品不存在或已删除：" + productId, 400);
        }
    }

    /**
     * 同一作物下同一产品只能配一行。
     *
     * <p>DB 有 {@code uk_crop_product} 兜底，但直接撞唯一键报出来的是一串 SQL 异常，
     * 用户看不懂；这里先给一句人话。</p>
     */
    private void assertNotDuplicated(Long cropId, Long productId, Long excludeId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<CropProduct>()
            .eq(CropProduct::getCropId, cropId)
            .eq(CropProduct::getProductId, productId)
            .ne(excludeId != null, CropProduct::getId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("该产品已在本作物的产品配置里，请勿重复添加", 400);
        }
    }
}
