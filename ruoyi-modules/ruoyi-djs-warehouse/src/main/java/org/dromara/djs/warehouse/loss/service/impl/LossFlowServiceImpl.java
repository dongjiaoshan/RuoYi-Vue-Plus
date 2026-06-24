package org.dromara.djs.warehouse.loss.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.domain.vo.LossFlowVo;
import org.dromara.djs.warehouse.loss.mapper.LossFlowMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 统一损耗流水台账 Service 实现（WMS-LOSS-001，行58）。
 *
 * @author djs
 * @since WMS-LOSS-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LossFlowServiceImpl implements ILossFlowService {

    private final LossFlowMapper lossFlowMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;

    @Override
    public Long record(LossFlow entity) {
        if (entity == null || entity.getLossType() == null) {
            return null;
        }
        // 不记录零/负损耗（账面无意义，且避免总览/详情噪声）
        if (entity.getLossWeight() == null || entity.getLossWeight().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (entity.getLossDate() == null) {
            entity.setLossDate(new Date());
        }
        // 回填产品快照（productId = product_info.id）
        if (entity.getProductId() != null && entity.getProductCode() == null) {
            ProductInfo product = productInfoMapper.selectById(entity.getProductId());
            if (product != null) {
                entity.setProductCode(product.getProductId());   // 业务码（String）
                entity.setProductName(product.getProductName());
                entity.setProductUnit(product.getProductUnit());
                if (entity.getBelongType() == null) {
                    entity.setBelongType(product.getBelongType());
                }
            }
        }
        // 回填库位名称
        if (entity.getLocationId() != null && entity.getLocationName() == null) {
            LocationInfo loc = locationInfoMapper.selectById(entity.getLocationId());
            if (loc != null) {
                entity.setLocationName(loc.getLocationName());
            }
        }
        lossFlowMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public Long record(String lossType, Long productId, BigDecimal lossWeight, Long locationId,
                       Long operatorId, String sourceBizType, Long sourceBizId, Long sourceFlowId) {
        LossFlow entity = new LossFlow();
        entity.setLossType(lossType);
        entity.setProductId(productId);
        entity.setLossWeight(lossWeight);
        entity.setLocationId(locationId);
        entity.setOperatorId(operatorId);
        entity.setSourceBizType(sourceBizType);
        entity.setSourceBizId(sourceBizId);
        entity.setSourceFlowId(sourceFlowId);
        return record(entity);
    }

    @Override
    public List<LossFlowVo> listByProduct(Long productId, Date dateFrom, Date dateTo) {
        if (productId == null) {
            return Collections.emptyList();
        }
        // V1 固定租户 '1001'；自定义 @Select 不走拦截器自动注入，显式取当前租户传入 WHERE。
        String tenantId = TenantHelper.getTenantId();
        return lossFlowMapper.selectByProduct(tenantId, productId, dateFrom, dateTo);
    }

}
