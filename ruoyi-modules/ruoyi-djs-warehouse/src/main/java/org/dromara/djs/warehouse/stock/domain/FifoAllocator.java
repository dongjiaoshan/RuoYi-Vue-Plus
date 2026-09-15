package org.dromara.djs.warehouse.stock.domain;

import org.dromara.common.core.exception.ServiceException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把一次出库 / 转移的总量按<b>先进先出</b>摊到一组库存篮上（V6 row223/row224 / D-0068
 * 「出库先进先出自动，工人不再手选哪一篮」）。
 *
 * <p>做成不依赖任何 bean 的纯函数，是因为它有<b>两个</b>调用方：库存查询页的行内出库 / 猪肉转移
 * （{@code LocationStockServiceImpl}）与毛菜间出库（{@code VegOutServiceImpl}）。两处各写一份，
 * 分叉的表现会是「同样一行货从不同页面出，扣的篮不一样」——而库存总量看起来还是对的，
 * 只有追溯到地块 / 耳号时才会发现对不上，那时已经很难倒查。</p>
 *
 * @author djs
 */
public final class FifoAllocator {

    private FifoAllocator() {
    }

    /**
     * @param baskets 库存篮，<b>调用方须已按先进先出排好</b>（建篮时间升序、同刻按 id 升序）
     * @param total   要出的总量
     * @return 篮 id → 该篮应扣数量，保序；分到 0 的篮不进结果
     * @throws ServiceException 篮组为空，或总量超过这组篮的库存合计
     */
    public static Map<Long, BigDecimal> allocate(List<LocationStock> baskets, BigDecimal total) {
        if (baskets == null || baskets.isEmpty()) {
            throw new ServiceException("未指定要出库的库存行", 400);
        }
        List<LocationStock> group = dedupeById(baskets);
        assertSameGroup(group);
        BigDecimal want = total == null ? BigDecimal.ZERO : total;
        BigDecimal available = BigDecimal.ZERO;
        for (LocationStock b : group) {
            available = available.add(nz(b.getProductStock()));
        }
        // 先比总量再动手：逐篮扣到一半才发现不够只能靠事务回滚，而那时报的错会指向某一个篮的余额，
        // 与页面上那一行显示的合计对不上，工人无从判断到底还能出多少。
        if (want.compareTo(available) > 0) {
            String name = group.get(0).getProductName();
            throw new ServiceException((name == null ? "该行" : "「" + name + "」")
                + "出库量(" + want.stripTrailingZeros().toPlainString()
                + ")超过该行库存(" + available.stripTrailingZeros().toPlainString() + ")", 400);
        }
        Map<Long, BigDecimal> plan = new LinkedHashMap<>();
        BigDecimal remaining = want;
        for (LocationStock b : group) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = remaining.min(nz(b.getProductStock()));
            // 空篮跳过：扣 0 会白写一条 0 量的流水，出库记录 / 出库明细里多出一行看不懂的空行。
            if (take.signum() <= 0) {
                continue;
            }
            plan.put(b.getId(), take);
            remaining = remaining.subtract(take);
        }
        return plan;
    }

    /**
     * 这一组篮必须真是列表上的<b>同一行</b>：同产品 + 同库位 + 同耳号 + 同地块 + 同三期。
     *
     * <p>改成按组扣之后，篮 id 组是前端传的，不再像「一个入参绑死一个篮」那样天然自证。
     * 实测裸调接口传 {@code [红薯篮42kg, 五花肉篮146kg]}，服务端会把它们当成一行、合计 188kg：
     * 量够就真提交，按先进先出先扣红薯再扣五花肉，还把同一个单价打到两条不同产品的流水上；
     * 「计数类单位只能填整数」那道闸也会因为「取第一篮判单位」而对第二个产品失效
     * （190kg 的紫线茄与 19000 个的提袋被并成一个池子）。</p>
     *
     * <p>库存数不会被放大（逐篮原子扣减那道闸还在），坏的是单据、计价与单位口径。
     * 判据放在这里而不是各调用方：库存查询行内出库 / 猪肉转移 / 毛菜间出库三条路共用本方法，
     * 各写一份迟早漏掉一条。</p>
     */
    private static void assertSameGroup(List<LocationStock> baskets) {
        String first = groupKey(baskets.get(0));
        for (LocationStock b : baskets) {
            if (!first.equals(groupKey(b))) {
                throw new ServiceException("这一行提交的库存篮不属于同一个产品 / 库位 / 耳号 / 地块，无法出库", 400);
            }
        }
    }

    private static String groupKey(LocationStock b) {
        return b.getProductId() + "|" + b.getMedicineId() + "|" + b.getLocationId()
            + "|" + (b.getEarNo() == null ? "" : b.getEarNo())
            + "|" + b.getPlotId()
            + "|" + LocationStock.thirdPhaseOf(b);
    }

    /**
     * 按 id 去重并保序；顺带剔除 null 元素（毛菜间出库侧是 {@code map(stocks::get)} 取的，
     * 取不到就是 null）。
     *
     * <p>去重而不是报错：重复 id 是「同一个篮说了两遍」，去掉之后语义无损、结果正确；
     * 而 {@link #assertSameGroup} 拦的跨组篮是「把两个产品当成一行」，那是语义错误，必须报。
     * 两者性质不同，处置也不同。</p>
     */
    private static List<LocationStock> dedupeById(List<LocationStock> baskets) {
        List<LocationStock> unique = new ArrayList<>(baskets.size());
        Set<Long> seen = new HashSet<>();
        for (LocationStock b : baskets) {
            if (b != null && b.getId() != null && seen.add(b.getId())) {
                unique.add(b);
            }
        }
        if (unique.isEmpty()) {
            throw new ServiceException("未指定要出库的库存行", 400);
        }
        return unique;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
