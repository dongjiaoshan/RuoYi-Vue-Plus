package org.dromara.djs.store.demand.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 门店视角两列派生指标的<b>唯一口径</b>：预计到店量 {@code expectedWeight} + 计损量 {@code damagedCount}。
 *
 * <p>两处门店端读接口都要用同一份口径，所以抽出来而不是各写一份：</p>
 * <ul>
 *   <li>admin / mp 门店需求<b>分页列表</b> —— {@code StoreDemandServiceImpl.queryStoreList}</li>
 *   <li>mp 门店需求<b>按天明细</b> —— {@code StoreDemandAppletServiceImpl.queryDayDetail}（V6 row76）</li>
 * </ul>
 *
 * <p>两条链路曾经只有前者回填，结果同一笔需求「列表页有预计到店量、点进详情就没有」。
 * 复制第二份实现必然再漂一次，故收口到本类，调用方只准调 {@link #enrich(List)}。</p>
 *
 * <p>compute-on-read：两个数都不持久化（白条已发货重随发货变、损坏件随生产明细变）。</p>
 *
 * @author djs
 * @since V6-row76
 */
@Component
@RequiredArgsConstructor
public class StoreDemandViewEnricher {

    /** 门店视角派生状态：已发货（{@code djs_store_demand_status} 之一）。 */
    private static final String STORE_STATUS_SHIPPED = "SHIPPED";

    /** 门店视角派生状态：确认到店（{@code djs_store_demand_status} 之一，SHIPPED 收货后）。 */
    private static final String STORE_STATUS_ARRIVED = "ARRIVED";

    /** 自产归属类型（djs_belong_type）：果蔬 / 猪肉 / 白条 / 干货 —— 预计到店重量口径分流。 */
    private static final String BELONG_VEGETABLE = "vegetable";
    private static final String BELONG_PORK = "pork";
    private static final String BELONG_WHITE_BAR = "white_bar";
    private static final String BELONG_DRY_GOOD = "dry_good";

    private final ProductInfoMapper productInfoMapper;

    private final ProductProductionMapper productProductionMapper;

    private final IProductProductionService productProductionService;

    /**
     * 一次回填两列（顺序无关，互不依赖）。
     *
     * @param rows 已派生 {@code storeDemandStatus} 的门店视角行；null / 空直接返回
     */
    public void enrich(List<DemandManageVo> rows) {
        fillDamagedCount(rows);
        fillExpectedWeight(rows);
    }

    /**
     * 回填「预计到店重量」{@code expectedWeight}（admin row5，kg，前端按 belongType 换单位展示）。
     *
     * <p>按产品自产归属类型（belong_type，经 product_id JOIN 商品主数据取）分流：</p>
     * <ul>
     *   <li>果蔬(vegetable) / 猪肉(pork)：该需求<b>已发货实际称重之和</b>（kg）；前端 ×1000 展示 g。</li>
     *   <li>白条(white_bar)：该需求已发货白条实际重量之和（kg，Kevin 2026-07-07 定 a 方案）；前端 kg 不换算。</li>
     *   <li>干货(dry_good)：该需求已发货实际重量之和（kg）；前端 ×1000 展示 g。</li>
     *   <li>其余（鸡蛋 / 物资 / 礼盒 / 规格非重量）：不回填（{@code null}），前端展示 '—'。</li>
     * </ul>
     */
    private void fillExpectedWeight(List<DemandManageVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> productIds = rows.stream().map(DemandManageVo::getProductId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> belongByProduct = new HashMap<>();
        if (!productIds.isEmpty()) {
            List<ProductInfo> products = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .in(ProductInfo::getId, productIds)
                .select(ProductInfo::getId, ProductInfo::getBelongType));
            for (ProductInfo p : products) {
                belongByProduct.put(p.getId(), p.getBelongType());
            }
        }
        for (DemandManageVo vo : rows) {
            if (vo.getExpectedWeight() != null || vo.getProductId() == null) {
                continue;
            }
            String belong = belongByProduct.get(vo.getProductId());
            if (BELONG_WHITE_BAR.equals(belong)) {
                BigDecimal w = productProductionMapper.sumShippedWhiteBarWeightByDemand(vo.getId());
                if (w != null && w.signum() > 0) {
                    vo.setExpectedWeight(w);
                }
            } else if (BELONG_VEGETABLE.equals(belong) || BELONG_PORK.equals(belong)) {
                // row49/51：果蔬 / 猪肉预计到店重量按「已发货实际称重之和」（kg），非规格×需求量估算。
                // 刚下单待确认时无发货清点行 → 0 → 不回填（前端 '—'），发货后按实际称重回填。
                BigDecimal w = productProductionMapper.sumShippedVegPorkWeightByDemand(vo.getId());
                if (w != null && w.signum() > 0) {
                    vo.setExpectedWeight(w);
                }
            } else if (BELONG_DRY_GOOD.equals(belong)) {
                // 干货自带真实 product_weight（如 干羊肚菌 80g/份→0.08~0.10kg），生产时逐份称重
                // → 已发货后按实际称重回填，与白条/果蔬同口径。
                BigDecimal w = productProductionMapper.sumShippedDryGoodWeightByDemand(vo.getId());
                if (w != null && w.signum() > 0) {
                    vo.setExpectedWeight(w);
                }
            }
            // 其余 belong（礼盒为多产品组合、无单一整体重量，与鸡蛋按枚计一致 / 外购商品 / null）→ 不回填，前端 '—'
        }
    }

    /**
     * 回填「损坏数量」{@code damagedCount}（row48 + admin row6）：对门店派生状态为「已发货」(SHIPPED)
     * 或「确认到店」(ARRIVED) 的行，按 demand_id 统计损坏件数；其余行保持 {@code null}（前端展示 '—'）。
     *
     * <p>admin row6：确认到店后行由 SHIPPED 派生成 ARRIVED，损坏数量不因收货而清零（损坏件是产品生产明细
     * is_damaged=1 的固有属性，与收货无关），故收货后仍按同口径回填，不再漏算成 0。</p>
     *
     * <p>当前逐行查询（门店端单店分页 / 单店单日数据量小，契约为 COUNT 单点查询）。若后续单店需求行数
     * 显著增大，可在 warehouse 侧加批量 {@code countDamagedByDemandBatch(demandIds)} 一次聚合替代，去 N+1。</p>
     */
    private void fillDamagedCount(List<DemandManageVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (DemandManageVo vo : rows) {
            String s = vo.getStoreDemandStatus();
            if (vo.getId() != null && (STORE_STATUS_SHIPPED.equals(s) || STORE_STATUS_ARRIVED.equals(s))) {
                vo.setDamagedCount((int) productProductionService.countDamagedByDemand(vo.getId()));
            }
        }
    }
}
