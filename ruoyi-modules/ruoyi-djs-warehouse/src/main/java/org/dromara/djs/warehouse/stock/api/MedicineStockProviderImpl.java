package org.dromara.djs.warehouse.stock.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.medicine.api.MedicineProductDto;
import org.dromara.djs.common.medicine.api.MedicineStockProvider;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 药品库存提供方仓库侧实现（药品归仓库统一 · 药品即仓库商品 {@code buy_class='medicine'}）。
 *
 * <p>药品主数据落 {@code t_warehouse_product_info}（{@code buy_class='medicine'}）、库存真值落
 * {@code t_warehouse_location_stock}（product 维）。养殖 med 域经 common 的
 * {@link MedicineStockProvider} facade 消费药品清单 + 库存，零循环依赖。</p>
 *
 * <p>扣减 / 退回落到该药品商品库存所在库位（{@link LocationStockMapper#selectDefaultLocationByProduct}
 * 取库存最多的库位），复用仓库物资领用同一 product 维原子扣减范式（{@code WHERE product_stock>=qty}
 * 行锁 + 数量校验同步）。{@code tenant_id} 由 MP 拦截器在 final SQL 阶段注入。</p>
 *
 * <p>每次扣减 / 增加同步落一条 {@code t_warehouse_stock_flow} 出入库流水（口径对齐
 * {@code MatFlowServiceImpl}），供出入库记录页 / 库存总览流水回放对账：领用 dept_pick_out /
 * 损耗 loss（另双写统一损耗台账 {@code t_warehouse_loss_flow}，损耗总览数据源）/ 领用退回
 * pick_return_in / 批次采购入库 purchase_in。流水与库存增减在调用方（breed 侧
 * {@code insertByBo @Transactional}）同一事务内原子提交 / 回滚。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicineStockProviderImpl implements MedicineStockProvider {

    /** 药品图 belong_type（IMG-LIB-001 resolver L2 分类默认图键；无配置则回落全局默认/空）。 */
    private static final String MEDICINE_BELONG_TYPE = "medicine";

    /**
     * 出入库方向：IN=入 / OT=出（DDL CHAR(3)，对齐 {@code MatFlowServiceImpl}）。
     */
    private static final String INOUT_IN = "IN";
    private static final String INOUT_OUT = "OT";

    /**
     * 领用出库 flow_type（{@code djs_flow_type} 部门领用出库）：养殖端药品领用经 {@link #deduct}
     * 出库，口径对齐养殖 / 种植物资领用（FIX-WMS-FLOWDICT-003 的 dept_pick_out）。
     */
    private static final String FLOW_DEPT_PICK_OUT = "dept_pick_out";

    /**
     * 损耗出库 flow_type（{@code djs_flow_type} 损耗）：养殖端药品损耗经 {@link #deductLoss}
     * 出库，并双写统一损耗台账（对齐 {@code MatFlowServiceImpl} 物资损耗的 loss + manual_loss 范式）。
     */
    private static final String FLOW_LOSS = "loss";

    /**
     * 采购入库 flow_type（{@code djs_flow_type}）：养殖端药品批次入库（= 采购入库）经
     * {@link #addPurchase} 入库，计入采购入库记录页 / 产品月采购统计。
     */
    private static final String FLOW_PURCHASE_IN = "purchase_in";

    /**
     * 领用退回入库 flow_type（{@code djs_flow_type}）：养殖端药品领用退回经 {@link #add} 入库，
     * 口径对齐物资领用退回（pick_return_in，计入 mp 今日已退额度、不污染月采购统计）。
     */
    private static final String FLOW_PICK_RETURN_IN = "pick_return_in";

    /** 损耗台账 loss_type（字典 {@code djs_loss_type} 录入损耗，对齐物资领用损耗）。 */
    private static final String LOSS_TYPE_MANUAL = "manual_loss";

    /** 损耗台账来源业务标识（{@code t_warehouse_loss_flow.source_biz_type}）。 */
    private static final String LOSS_SOURCE_MED = "med";

    private final LocationStockMapper locationStockMapper;
    private final ImageUrlResolver imageUrlResolver;
    private final StockFlowMapper stockFlowMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ILossFlowService lossFlowService;

    @Override
    public List<MedicineProductDto> listMedicineProducts(String keyword) {
        return toDtoList(locationStockMapper.selectMedicineProducts(keyword));
    }

    @Override
    public List<MedicineProductDto> listMedicineProductsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return toDtoList(locationStockMapper.selectMedicineProductsByIds(ids));
    }

    /**
     * 药品商品行（id/name/unit/spec/imageId/stock）→ {@link MedicineProductDto}。
     * imageId 原为 {@code COALESCE(product_thumb, image_oss_id)} 裸 ossId，经 IMG-LIB-001 resolver
     * 批量转 OSS public URL（禁 N+1）供 mp {@code <image :src>} 直接渲染；无图返 null（mp 端占位兜底）。
     */
    private List<MedicineProductDto> toDtoList(List<Map<String, Object>> rows) {
        List<MedicineProductDto> result = new ArrayList<>(rows.size());
        List<ImageUrlResolver.Item> imgItems = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            MedicineProductDto dto = new MedicineProductDto();
            dto.setId(toLong(row.get("id")));
            dto.setName(toStr(row.get("name")));
            dto.setUnit(toStr(row.get("unit")));
            dto.setSpec(toStr(row.get("spec")));
            dto.setStock(toBigDecimal(row.get("stock")));
            result.add(dto);
            imgItems.add(new ImageUrlResolver.Item(toStr(row.get("imageId")), MEDICINE_BELONG_TYPE));
        }
        List<String> urls = imageUrlResolver.resolveList(imgItems);
        if (urls.size() == result.size()) {
            for (int i = 0; i < result.size(); i++) {
                result.get(i).setImageUrl(urls.get(i));
            }
        }
        return result;
    }

    @Override
    public void deduct(Long productId, BigDecimal qty, Long operatorId) {
        doDeduct(productId, qty, operatorId, FLOW_DEPT_PICK_OUT);
    }

    @Override
    public void deductLoss(Long productId, BigDecimal qty, Long operatorId) {
        StockFlow flow = doDeduct(productId, qty, operatorId, FLOW_LOSS);
        // 统一损耗台账双写（WMS-LOSS-001）：损耗总览 / 库存详情损耗 tab 数据源，
        // 关联本次 loss 出库流水；record 内回填产品/库位快照
        lossFlowService.record(LOSS_TYPE_MANUAL, productId, qty, flow.getWarehouseId(),
            operatorId, LOSS_SOURCE_MED, null, flow.getId());
    }

    /**
     * 扣减共链：解析默认库位 → 原子扣减（{@code WHERE product_stock>=qty}）→ 落出库流水。
     *
     * @return 已落库的出库流水（供损耗路径关联 {@code loss_flow.source_flow_id}）
     */
    private StockFlow doDeduct(Long productId, BigDecimal qty, Long operatorId, String flowType) {
        Long locationId = locationStockMapper.selectDefaultLocationByProduct(productId);
        if (locationId == null) {
            throw new ServiceException("药品无库存，无法领用：" + productId);
        }
        int aff = locationStockMapper.deductByProductLocation(locationId, productId, qty, operatorId);
        if (aff == 0) {
            throw new ServiceException("药品库存不足");
        }
        return insertStockFlow(locationId, productId, qty, operatorId, INOUT_OUT, flowType);
    }

    @Override
    public void add(Long productId, BigDecimal qty, Long operatorId) {
        doAdd(productId, qty, operatorId, FLOW_PICK_RETURN_IN, "退回");
    }

    @Override
    public void addPurchase(Long productId, BigDecimal qty, Long operatorId) {
        doAdd(productId, qty, operatorId, FLOW_PURCHASE_IN, "入库");
    }

    /**
     * 增加共链：解析默认库位 → 加库存 → 落入库流水（退回 pick_return_in / 采购 purchase_in）。
     */
    private void doAdd(Long productId, BigDecimal qty, Long operatorId, String flowType, String action) {
        Long locationId = locationStockMapper.selectDefaultLocationByProduct(productId);
        if (locationId == null) {
            throw new ServiceException("药品无库位记录，无法" + action + "：" + productId);
        }
        int aff = locationStockMapper.addByProductLocation(locationId, productId, qty, operatorId);
        if (aff == 0) {
            throw new ServiceException("药品" + action + "失败：库存行不存在");
        }
        insertStockFlow(locationId, productId, qty, operatorId, INOUT_IN, flowType);
    }

    /**
     * 药品出入库落一条 {@code t_warehouse_stock_flow} 流水。
     *
     * <p>字段口径对齐 {@code MatFlowServiceImpl}：{@code flow_no} 走 {@link BizCodeType#STOCK_FLOW_NO}
     * （{@code F+yyyyMMdd+ioCode2+seq4}），{@code change_num} 出库为负 / 入库为正，
     * {@code change_quantity} 恒正，{@code warehouse_id} 写库位 id（D7 命名遗留，实为 location FK）。
     * 库存增减成功后才写流水；流水 INSERT 抛异常时由调用方事务连同库存扣减一起回滚。</p>
     *
     * @return 已落库的流水实体（id 由 MP 雪花回填）
     */
    private StockFlow insertStockFlow(Long locationId, Long productId, BigDecimal qty, Long operatorId,
                                      String inoutType, String flowType) {
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", inoutType);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(new Date());
        flow.setProductId(productId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(inoutType);
        flow.setFlowType(flowType);
        flow.setChangeNum(INOUT_OUT.equals(inoutType) ? qty.negate() : qty);
        flow.setChangeQuantity(qty);
        flow.setOperatorId(operatorId);
        stockFlowMapper.insert(flow);
        return flow;
    }

    @Override
    public Map<Long, BigDecimal> getStocks(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = locationStockMapper.selectProductStocks(productIds);
        Map<Long, BigDecimal> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long productId = toLong(row.get("product_id"));
            BigDecimal stock = toBigDecimal(row.get("stock"));
            if (productId != null && stock != null) {
                result.put(productId, stock);
            }
        }
        return result;
    }

    /**
     * {@code product_id} 列取值转 {@link Long}（JDBC 可能返 BigInteger / Long / Number）。
     */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.longValue();
        }
        return Long.valueOf(value.toString());
    }

    /**
     * {@code SUM(product_stock)} 列取值转 {@link BigDecimal}（MySQL SUM 返 BigDecimal，
     * 兜底处理 Number 子类）。
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal dec) {
            return dec;
        }
        if (value instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        return new BigDecimal(value.toString());
    }

    private String toStr(Object value) {
        return value == null ? null : value.toString();
    }

}
