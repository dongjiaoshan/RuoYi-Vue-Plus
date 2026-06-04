package org.dromara.djs.warehouse.shipment.returnpkg.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.util.I18nMessages;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.ReturnProduct;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnConfirmBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnProductBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.query.ReturnProductQuery;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnProductVo;
import org.dromara.djs.warehouse.shipment.returnpkg.mapper.ReturnProductMapper;
import org.dromara.djs.warehouse.shipment.returnpkg.service.IReturnProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 退货管理 Service 实现（WMS-SHIP-001）。
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Slf4j
@Service
public class ReturnProductServiceImpl
    extends DjsBaseServiceImpl<ReturnProductMapper, ReturnProduct>
    implements IReturnProductService {

    private static final String DIRECTION_STORE_TO_WAREHOUSE = "store_to_warehouse";

    private static final String FLOW_TYPE_RETURN_IN = "return_in";

    private static final String INOUT_IN = "IN";

    private static final String STATUS_PENDING = "pending";

    private static final String STATUS_CONFIRMED = "confirmed";

    private final StockFlowMapper stockFlowMapper;

    private final IBizCodeGenerator bizCodeGenerator;

    public ReturnProductServiceImpl(ReturnProductMapper baseMapper,
                                    StockFlowMapper stockFlowMapper,
                                    IBizCodeGenerator bizCodeGenerator) {
        super(baseMapper);
        this.stockFlowMapper = stockFlowMapper;
        this.bizCodeGenerator = bizCodeGenerator;
    }

    @Override
    public TableDataInfo<ReturnProductVo> queryPageList(ReturnProductQuery query, PageQuery pageQuery) {
        Page<ReturnProductVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(query));
        return TableDataInfo.build(page);
    }

    @Override
    public List<ReturnProductVo> queryList(ReturnProductQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public ReturnProductVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(ReturnProductBo bo) {
        ReturnProduct entity = MapstructUtils.convert(bo, ReturnProduct.class);
        // 业务默认
        entity.setReturnNo(generateReturnNo());
        entity.setApplyTime(bo.getApplyTime() == null ? LocalDateTime.now() : bo.getApplyTime());
        entity.setIsConfirm(0);
        entity.setReturnStatus(STATUS_PENDING);
        entity.setReturnDirection(StringUtils.isBlank(bo.getReturnDirection())
            ? DIRECTION_STORE_TO_WAREHOUSE : bo.getReturnDirection());
        baseMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(ReturnProductBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException(I18nMessages.t("return.id.required"));
        }
        ReturnProduct existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException(I18nMessages.t("return.not_found", bo.getId()), 404);
        }
        if (!STATUS_PENDING.equals(existing.getReturnStatus())) {
            throw new ServiceException(I18nMessages.t("return.status_immutable", existing.getReturnStatus()), 400);
        }
        ReturnProduct entity = MapstructUtils.convert(bo, ReturnProduct.class);
        // 不允许通过 update 改 returnNo / isConfirm / returnStatus / confirmUser 等关键字段
        entity.setReturnNo(null);
        entity.setIsConfirm(null);
        entity.setReturnStatus(null);
        entity.setConfirmUser(null);
        entity.setConfirmTime(null);
        entity.setConfirmWeight(null);
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        // 走 DjsBaseServiceImpl#softDelete
        return softDelete(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturn(Long id, ReturnConfirmBo bo) {
        Long userId = LoginHelper.getUserId();
        ReturnProduct entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException(I18nMessages.t("return.not_found", id), 404);
        }
        if (entity.getIsConfirm() != null && entity.getIsConfirm() == 1) {
            throw new ServiceException(I18nMessages.t("return.already_confirmed"), 400);
        }

        // 1. UPDATE 行
        LocalDateTime now = LocalDateTime.now();
        ReturnProduct upd = new ReturnProduct();
        upd.setId(id);
        upd.setIsConfirm(1);
        upd.setReturnStatus(STATUS_CONFIRMED);
        upd.setConfirmUser(userId);
        upd.setConfirmTime(now);
        upd.setConfirmWeight(bo.getConfirmWeight());
        if (StringUtils.isNotBlank(bo.getRemark())) {
            upd.setRemark(bo.getRemark());
        }
        baseMapper.updateById(upd);

        // 2. 仅 store_to_warehouse 方向触发 stock_flow（其他方向 V1 占位不联动）
        if (DIRECTION_STORE_TO_WAREHOUSE.equals(entity.getReturnDirection())) {
            StockFlow flow = new StockFlow();
            Map<String, Object> ctx = new HashMap<>(2);
            ctx.put("ioCode", INOUT_IN);
            flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
            flow.setFlowDate(new Date());
            flow.setProductId(entity.getProductId());
            flow.setInoutType(INOUT_IN);
            flow.setFlowType(FLOW_TYPE_RETURN_IN);
            flow.setChangeNum(bo.getConfirmWeight());
            flow.setChangeQuantity(bo.getConfirmWeight());
            flow.setOperatorId(userId);
            flow.setRemark("门店退货入库 return_no=" + entity.getReturnNo()
                + " store_id=" + entity.getStoreId());
            stockFlowMapper.insert(flow);
            log.info("[WMS-SHIP-001] confirmReturn returnId={} → stock_flow return_in confirmWeight={}",
                id, bo.getConfirmWeight());
        } else {
            log.info("[WMS-SHIP-001] confirmReturn returnId={} direction={} placeholder（不联动 stock_flow，V2 实现）",
                id, entity.getReturnDirection());
        }
    }

    // ---------- private helpers ----------

    private LambdaQueryWrapper<ReturnProduct> buildQueryWrapper(ReturnProductQuery q) {
        LambdaQueryWrapper<ReturnProduct> w = new LambdaQueryWrapper<>();
        if (q == null) {
            return w.orderByDesc(ReturnProduct::getId);
        }
        w.like(StringUtils.isNotBlank(q.getReturnNo()), ReturnProduct::getReturnNo, q.getReturnNo())
            .eq(q.getStoreId() != null, ReturnProduct::getStoreId, q.getStoreId())
            .eq(q.getProductId() != null, ReturnProduct::getProductId, q.getProductId())
            .eq(q.getIsConfirm() != null, ReturnProduct::getIsConfirm, q.getIsConfirm())
            .eq(StringUtils.isNotBlank(q.getReturnDirection()),
                ReturnProduct::getReturnDirection, q.getReturnDirection())
            .eq(StringUtils.isNotBlank(q.getReturnStatus()),
                ReturnProduct::getReturnStatus, q.getReturnStatus())
            .ge(q.getApplyDateFrom() != null, ReturnProduct::getApplyTime,
                q.getApplyDateFrom() == null ? null : q.getApplyDateFrom().atStartOfDay())
            .le(q.getApplyDateTo() != null, ReturnProduct::getApplyTime,
                q.getApplyDateTo() == null ? null : q.getApplyDateTo().atTime(23, 59, 59))
            .orderByDesc(ReturnProduct::getId);
        return w;
    }

    /**
     * 生成 return_no：{@code RET{yyyyMMdd}{seq4}}，走 {@link IBizCodeGenerator} 的
     * {@link BizCodeType#RETURN_NO} 规则（每日重置 + Redisson 锁 + 序号表 UNIQUE 双保护，
     * 与 BURN_NO / CUT_NO / BAR_NO 范式一致）。
     */
    private String generateReturnNo() {
        return bizCodeGenerator.generate(BizCodeType.RETURN_NO, Map.of());
    }
}
