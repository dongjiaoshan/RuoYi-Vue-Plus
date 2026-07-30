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
import org.dromara.djs.warehouse.stock.domain.LocationStock;
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

    @Override
    public List<Long> listRecentPickedMedicineIds(Long operatorId) {
        // row131：近 3 天已领药品 id 从仓库领用出库流水取（覆盖疫苗药品页 + 物资领用药品库两个入口，
        // 两入口领用都落 dept_pick_out 流水，天然一致）。
        return locationStockMapper.selectRecentPickedMedicineIds(operatorId);
    }

    /**
     * 药品商品行（id/code/name/unit/spec/supplierId/remark/imageId/stock）→ {@link MedicineProductDto}。
     * imageId 原为 {@code COALESCE(product_thumb, image_oss_id)} 裸 ossId，经 IMG-LIB-001 resolver
     * 批量转 OSS public URL（禁 N+1）供 mp {@code <image :src>} 直接渲染；无图返 null（mp 端占位兜底）。
     */
    private List<MedicineProductDto> toDtoList(List<Map<String, Object>> rows) {
        List<MedicineProductDto> result = new ArrayList<>(rows.size());
        List<ImageUrlResolver.Item> imgItems = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            MedicineProductDto dto = new MedicineProductDto();
            dto.setId(toLong(row.get("id")));
            dto.setCode(toStr(row.get("code")));
            dto.setName(toStr(row.get("name")));
            dto.setUnit(toStr(row.get("unit")));
            dto.setSpec(toStr(row.get("spec")));
            dto.setSupplierId(toLong(row.get("supplierId")));
            dto.setRemark(toStr(row.get("remark")));
            dto.setStock(toBigDecimal(row.get("stock")));
            // row129：今日三量（selectMedicineProducts 回传；selectMedicineProductsByIds 无此列 → 兜 0）
            dto.setTodayPicked(toBigDecimal(row.get("todayPicked")));
            dto.setTodayReturned(toBigDecimal(row.get("todayReturned")));
            dto.setTodayLoss(toBigDecimal(row.get("todayLoss")));
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
        // 药品损耗不二次扣库存（root cause 修复）：药品无 inhouse「待打包」池（无打包链），领用（deduct）
        // 已从 location_stock 扣过一次真值，损耗此时只记录流水 + 双写损耗台账即可；若再走 doDeduct
        // → deductByProductLocation 会二次扣 location_stock（对齐 MatFlowServiceImpl.loss() 对
        // 可打包食品原料「不再二次扣 location_stock、只剥 WIP」的口径，药品因无 WIP 故连剥都不需要）。
        StockFlow flow = insertStockFlowWithoutDeduct(productId, qty, operatorId, FLOW_LOSS);
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

    /**
     * 药品损耗专用：解析默认库位 → 落一条 loss 出库流水，<b>不扣 location_stock</b>。
     *
     * <p>与 {@link #doDeduct} 的唯一区别：省去 {@code deductByProductLocation} 那步。药品库存真值已在
     * 领用（{@link #deduct}）时从 {@code location_stock} 扣过一次，损耗再扣即二次扣减。故损耗只记流水
     * （供出入库记录 / 损耗总览回放）+ 由调用方双写损耗台账，库存不再变动。</p>
     *
     * <p>库位解析仍走 {@link LocationStockMapper#selectDefaultLocationByProduct}：为 null（该药品无任何库存行）
     * 时仍抛 {@link ServiceException}，不吞异常——流水的 {@code warehouse_id} 需要一个真实库位快照。</p>
     *
     * @return 已落库的 loss 出库流水（供损耗台账关联 {@code loss_flow.source_flow_id}）
     */
    private StockFlow insertStockFlowWithoutDeduct(Long productId, BigDecimal qty, Long operatorId, String flowType) {
        Long locationId = locationStockMapper.selectDefaultLocationByProduct(productId);
        if (locationId == null) {
            throw new ServiceException("药品无库存记录，无法登记损耗：" + productId);
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
     * 增加共链：解析落位库位 → 加库存（无库存行则兜底建账）→ 落入库流水
     * （退回 pick_return_in / 采购 purchase_in）。
     *
     * <p><b>零库存药品首次入库必须能成功</b>：药品目录（{@code buy_class='medicine'}）绝大多数是
     * 「已建主数据、还没进过货」的零库存药品，此时 {@link LocationStockMapper#selectDefaultLocationByProduct}
     * 与 {@link LocationStockMapper#addByProductLocation} 都命中不到任何行。故本方法按
     * {@code ProductInfoServiceImpl.inbound} 同一范式兜底 INSERT 一条产品维度库存行
     * （{@code plot_id / ear_no / white_bar_no} 全 NULL），落位库位由
     * {@link #resolveInboundLocation} 解析。库位与库存行的解析失败一律抛
     * {@link ServiceException}（不静默建到错库位），且失败时不写流水 —— 保持
     * 「库存增减成功才落流水」的双账簿一致性。</p>
     */
    private void doAdd(Long productId, BigDecimal qty, Long operatorId, String flowType, String action) {
        Map<String, Object> meta = null;
        Long locationId = locationStockMapper.selectDefaultLocationByProduct(productId);
        if (locationId == null) {
            meta = requireProductMeta(productId, action);
            locationId = resolveInboundLocation(meta, productId, action);
        }
        int aff = locationStockMapper.addByProductLocation(locationId, productId, qty, operatorId);
        if (aff == 0) {
            // 该 (库位,产品) 尚无产品维度（非篮子）库存行 → 首次入库建账
            if (meta == null) {
                meta = requireProductMeta(productId, action);
            }
            insertProductStockRow(locationId, productId, qty, operatorId, meta);
        }
        insertStockFlow(locationId, productId, qty, operatorId, INOUT_IN, flowType);
    }

    /**
     * 取产品元信息（名称 / 单位 / 配置存储库位），产品不存在或已软删则抛异常。
     */
    private Map<String, Object> requireProductMeta(Long productId, String action) {
        Map<String, Object> meta = locationStockMapper.selectProductInboundMeta(productId);
        if (meta == null) {
            throw new ServiceException("药品不存在或已删除，无法" + action + "：" + productId);
        }
        return meta;
    }

    /**
     * 解析首次入库落位库位：产品配置的存储库位（{@code product_info.store_location_id} 首个有效项）
     * → 兜底「药品库」库位；两者都拿不到抛 {@link ServiceException}。
     *
     * <p>不用 {@link LocationStockMapper#selectDefaultMedicineLocationId}（按
     * {@code location_type='medicine'} 查，而药品库的 {@code location_type} 是 {@code farm_loc}，
     * 该方法恒返 null）。</p>
     */
    private Long resolveInboundLocation(Map<String, Object> meta, Long productId, String action) {
        Long configured = firstConfiguredLocationId(toStr(meta.get("storeLocationId")));
        if (configured != null) {
            return configured;
        }
        Long medicineWarehouse = locationStockMapper.selectMedicineWarehouseLocationId();
        if (medicineWarehouse == null) {
            throw new ServiceException("药品未配置存储库位且未建「药品库」库位，无法" + action + "：" + productId);
        }
        return medicineWarehouse;
    }

    /**
     * 取 {@code store_location_id}（逗号分隔多库位，V2 改关联表）里第一个有效库位 id；
     * 空 / 全为非法值返 null（调用方兜底到药品库）。非法项 warn 出来，不静默丢。
     */
    private Long firstConfiguredLocationId(String storeLocationId) {
        if (storeLocationId == null || storeLocationId.isBlank()) {
            return null;
        }
        for (String token : storeLocationId.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                return Long.valueOf(trimmed);
            } catch (NumberFormatException e) {
                log.warn("product_info.store_location_id 含非法库位 id，已跳过：{}（原值 {}）", trimmed, storeLocationId);
            }
        }
        return null;
    }

    /**
     * 首次入库建账：INSERT 一条产品维度库存行（{@code plot_id / ear_no / white_bar_no} 全 NULL，
     * 与 {@link LocationStockMapper#addByProductLocation} 的命中条件对齐，后续入库走 UPDATE 累加）。
     *
     * <p>{@code product_name} / {@code product_unit} 是 DDL NOT NULL 的冗余列，取产品主数据快照；
     * {@code is_end=0}（未用完，否则不进库存合计口径）。</p>
     */
    private void insertProductStockRow(Long locationId, Long productId, BigDecimal qty, Long operatorId,
                                       Map<String, Object> meta) {
        LocationStock stock = new LocationStock();
        stock.setLocationId(locationId);
        stock.setProductId(productId);
        stock.setProductName(toStr(meta.get("productName")));
        stock.setProductUnit(toStr(meta.get("productUnit")));
        stock.setProductStock(qty);
        stock.setIsEnd(0);
        stock.setOperatorId(operatorId);
        locationStockMapper.insert(stock);
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

    @Override
    public Map<String, BigDecimal> todayFlowStat(Long medicineId) {
        Map<String, BigDecimal> stat = new HashMap<>(4);
        stat.put("use", BigDecimal.ZERO);
        stat.put("return", BigDecimal.ZERO);
        stat.put("loss", BigDecimal.ZERO);
        if (medicineId == null) {
            return stat;
        }
        Map<String, Object> row = locationStockMapper.selectMedicineTodayFlowStat(medicineId);
        if (row != null) {
            BigDecimal use = toBigDecimal(row.get("useQty"));
            BigDecimal ret = toBigDecimal(row.get("returnQty"));
            BigDecimal loss = toBigDecimal(row.get("lossQty"));
            if (use != null) {
                stat.put("use", use);
            }
            if (ret != null) {
                stat.put("return", ret);
            }
            if (loss != null) {
                stat.put("loss", loss);
            }
        }
        return stat;
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
