package org.dromara.djs.warehouse.demand.core;

import lombok.RequiredArgsConstructor;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 「到店量」{@code arrivedQuantity} 批量回填的<b>唯一实现</b>（V6-R161 口径，V6-R197 起被状态派生依赖）。
 *
 * <p>抽出来的原因：R197 之后门店视角状态 {@link StoreDemandStatusMapping#derive} <b>必须先有到店量</b>
 * 才能算（到店量 0 → 已确认 / 不足 → 部分到店 / 够 → 已发货）。而回填到店量的地方原本只有 store 模块的
 * {@code StoreDemandViewEnricher} 一处，warehouse 侧 {@code DemandManageServiceImpl.queryPageList}
 * （需求确认抽屉的数据源）根本不回填。两边各写一份必然漂，故收口到 warehouse 侧本类，
 * 三个调用方（warehouse 分页列表 / store 分页列表 / mp 按天明细）共用。</p>
 *
 * <p>口径 = 该需求下已被发货清点（{@code is_delivery_check = 1}）的成品<b>条数</b>，与需求量同单位。
 * 这个标记与 {@code demand_id} 是点击发车那一刻同事务写入的，锚的正是「点击发车时的产品数据」。
 * 不用需求单上的 {@code shipped_count}（那个在打包送到发货月台时就累加了，不是发车），
 * 也不用发货流水的 {@code ship_quantity}（白条链路上它装的是 kg，与按份/头计的需求量并排会串味）。</p>
 *
 * @author djs
 * @since V6-R197
 */
@Component
@RequiredArgsConstructor
public class DemandArrivedQuantityFiller {

    private final ProductProductionMapper productProductionMapper;

    /**
     * 批量回填 {@code arrivedQuantity}（禁 N+1，一页一次聚合）。
     *
     * <p>没有任何发车记录的需求回填 {@code 0} 而不是留 null —— 「还没发车」在业务上就是到店 0，
     * 与同页「损坏数量」显 0 同处置。<b>已有非 null 值的行跳过</b>：上游（warehouse 分页列表）
     * 已经填过时，下游（store enricher）不再重复打库，也不覆盖上游结果。</p>
     *
     * @param rows 门店视角行；null / 空 / 全部已填 → 不打库直接返回
     */
    public void fill(List<DemandManageVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> demandIds = rows.stream()
            .filter(vo -> vo.getArrivedQuantity() == null)
            .map(DemandManageVo::getId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (demandIds.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> arrivedByDemand = resolve(demandIds);
        for (DemandManageVo vo : rows) {
            if (vo.getId() == null || vo.getArrivedQuantity() != null) {
                continue;
            }
            vo.setArrivedQuantity(arrivedByDemand.getOrDefault(vo.getId(), BigDecimal.ZERO));
        }
    }

    /**
     * 按需求 id 批量取到店量。
     *
     * @param demandIds 需求主键集合（null / 空 → 空 map，不打库）
     * @return {@code demandId → 到店量}；一件都没发车的需求<b>不在</b> map 里（调用方按 0 兜底）
     */
    public Map<Long, BigDecimal> resolve(Collection<Long> demandIds) {
        Map<Long, BigDecimal> arrivedByDemand = new HashMap<>();
        if (demandIds == null || demandIds.isEmpty()) {
            return arrivedByDemand;
        }
        for (Map<String, Object> r : productProductionMapper.selectArrivedQuantityByDemandIds(demandIds)) {
            Object id = r.get("demandId");
            Object qty = r.get("arrivedQty");
            if (id instanceof Number n && qty instanceof Number q) {
                arrivedByDemand.put(n.longValue(), new BigDecimal(q.toString()));
            }
        }
        return arrivedByDemand;
    }
}
