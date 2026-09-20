package org.dromara.djs.plant.market.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.common.domain.vo.DateWindowStatusStatVo;
import org.dromara.djs.plant.common.util.DateWindowStatusCalculator;
import org.dromara.djs.plant.market.domain.query.MarketPlanQuery;
import org.dromara.djs.plant.market.domain.vo.MarketPlanVo;
import org.dromara.djs.plant.market.mapper.MarketPlanMapper;
import org.dromara.djs.plant.market.service.IMarketPlanService;
import org.dromara.djs.plant.market.util.MarketStatusLabel;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 果蔬上市计划实现（V6-R151）。
 *
 * <p>纯只读聚合 service（非 {@code DjsBaseServiceImpl}，无 CRUD / softDelete）：
 * 取聚合行 → 批量解析作物图 URL → 现算上市状态 → 返回。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPlanServiceImpl implements IMarketPlanService {

    private static final String DEFAULT_TENANT = "1001";

    /** 全量取行的软上限：超过只告警不截断（与 overview 导出的 5000 行约定对齐）。 */
    private static final int FULL_SCAN_WARN_ROWS = 5000;

    private final MarketPlanMapper marketPlanMapper;

    private final ImageUrlResolver imageUrlResolver;

    /**
     * {@inheritDoc}
     *
     * <h3>状态筛选为什么不走 SQL</h3>
     * 状态是「上市 / 下架日期 vs 当天」现算的派生值，库里没有这一列。要让筛选跨页生效只有两条路：
     * <ol>
     *   <li>把五档规则反解成 SQL 的日期区间谓词，下推 WHERE，继续由 SQL 分页；</li>
     *   <li>取全量 → Java 算状态 → 过滤 → 在内存里切页。</li>
     * </ol>
     * <b>选 2，放弃 1 的分页下推性能。</b>理由：路 1 会把同一套 30/15 天判定规则复制成 SQL 和 Java 两份，
     * 这正是项目铁律禁止的「两边兼容」—— 两份逻辑会在下一次甲方调分界天数时静默分叉，
     * 而分叉的表现是「筛出来的行和它自己显示的状态对不上」，极难发现。判定只留
     * {@link DateWindowStatusCalculator} 一份，SQL 永远不认识状态。
     *
     * <p>代价是带状态筛选时要全量扫一遍（本页当前 109 行，同一条 SQL 的全量版本本来就在导出路径上跑着，
     * 不是新增的负担）。不带状态筛选时仍走 SQL 分页，不受影响。</p>
     *
     * <p>两条路径共用同一条 SQL、同一个 {@code ORDER BY}，所以内存切页的行序与 SQL 分页完全一致。</p>
     */
    @Override
    public TableDataInfo<MarketPlanVo> queryPageList(MarketPlanQuery query, PageQuery pageQuery) {
        // PageQuery 构造参数顺序为 (pageSize, pageNum)
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(10, 1);
        if (query != null && StrUtil.isNotBlank(query.getMarketStatus())) {
            return pageFilteredInMemory(query, pq);
        }
        IPage<MarketPlanVo> page = marketPlanMapper.selectMarketPlanPage(buildFixedOrderPage(pq), currentTenant(), query);
        fillImageUrl(page.getRecords());
        fillMarketStatus(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<MarketPlanVo> queryList(MarketPlanQuery query) {
        List<MarketPlanVo> list = marketPlanMapper.selectMarketPlanList(currentTenant(), query);
        if (list == null) {
            return List.of();
        }
        if (list.size() > FULL_SCAN_WARN_ROWS) {
            log.warn("[MarketPlan] 全量取行 {} 超过软上限 {}，未截断（导出注意 Excel 生成耗时，带状态筛选的列表注意响应耗时）",
                list.size(), FULL_SCAN_WARN_ROWS);
        }
        fillImageUrl(list);
        fillMarketStatus(list);
        return filterByStatus(list, query);
    }

    @Override
    public DateWindowStatusStatVo statusStat(MarketPlanQuery query) {
        // 统计忽略状态条件本身（选中一档后另外四档全 0 的版块没有意义），其余筛选照常生效
        MarketPlanQuery scope = new MarketPlanQuery();
        if (query != null) {
            scope.setCropName(query.getCropName());
            scope.setMarketBeginMonth(query.getMarketBeginMonth());
            scope.setMarketEndMonth(query.getMarketEndMonth());
        }
        List<MarketPlanVo> rows = marketPlanMapper.selectMarketPlanList(currentTenant(), scope);
        LocalDate today = LocalDate.now();
        Map<String, Integer> counts = new HashMap<>();
        if (rows != null) {
            for (MarketPlanVo vo : rows) {
                String status = resolveStatus(vo, today);
                if (status != null) {
                    counts.merge(status, 1, Integer::sum);
                }
            }
        }
        return DateWindowStatusStatVo.of(counts);
    }

    /**
     * 带状态筛选时的分页：全量 → 过滤 → 内存切页。行序由 SQL 的 {@code ORDER BY} 保证，这里不重排。
     *
     * <p>图片 URL 在 {@link #queryList} 里对全量行解析（一次批量调用），比只解析当前页多做一点，
     * 换来的是列表 / 导出 / 统计三条路径共用同一个方法、不产生第二份填充逻辑。</p>
     *
     * @param query     查询条件（含状态）
     * @param pageQuery 分页参数
     * @return 当前页数据 + 过滤后总数
     */
    private TableDataInfo<MarketPlanVo> pageFilteredInMemory(MarketPlanQuery query, PageQuery pageQuery) {
        List<MarketPlanVo> matched = queryList(query);
        int pageNum = pageQuery.getPageNum() == null ? PageQuery.DEFAULT_PAGE_NUM : Math.max(1, pageQuery.getPageNum());
        int pageSize = pageQuery.getPageSize() == null ? PageQuery.DEFAULT_PAGE_SIZE : Math.max(1, pageQuery.getPageSize());
        int from = Math.min((pageNum - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());
        return new TableDataInfo<>(new ArrayList<>(matched.subList(from, to)), matched.size());
    }

    /**
     * 按状态码过滤已填好状态的行；条件为空时原样返回。
     *
     * @param rows  已经过 {@link #fillMarketStatus} 的行
     * @param query 查询条件（可空）
     * @return 过滤后的行
     */
    private List<MarketPlanVo> filterByStatus(List<MarketPlanVo> rows, MarketPlanQuery query) {
        if (query == null || StrUtil.isBlank(query.getMarketStatus())) {
            return rows;
        }
        String wanted = query.getMarketStatus().trim();
        List<MarketPlanVo> matched = new ArrayList<>();
        for (MarketPlanVo vo : rows) {
            if (wanted.equals(vo.getMarketStatus())) {
                matched.add(vo);
            }
        }
        return matched;
    }

    /**
     * 分页对象：丢掉请求参数 {@code orderByColumn}/{@code isAsc} 带来的排序项。
     *
     * <p>MP 分页拦截器把 {@code PageQuery} 的排序项拼在 SQL 自带 ORDER BY <b>之前</b>，
     * 所以 URL 上随手带个 {@code ?orderByColumn=cropName&isAsc=asc} 就能静默压过甲方要求的
     * 「按上市月份降序」，且不报错、不留痕。本页原型没有可点排序的列，直接清掉，
     * 把排序变成服务端保证而不是前端自觉。</p>
     *
     * @param pageQuery 分页参数
     * @return 已清空排序项的分页对象
     */
    private Page<MarketPlanVo> buildFixedOrderPage(PageQuery pageQuery) {
        Page<MarketPlanVo> page = pageQuery.build();
        page.orders().clear();
        return page;
    }

    /**
     * 批量把行上的 {@code cropImage}（ossId）解析成 OSS URL，写回 {@code cropImageUrl}。禁 N+1。
     *
     * @param rows 待填充行（可空）
     */
    private void fillImageUrl(List<MarketPlanVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> ossIds = new HashSet<>();
        for (MarketPlanVo vo : rows) {
            if (StrUtil.isNotBlank(vo.getCropImage())) {
                ossIds.add(vo.getCropImage().trim());
            }
        }
        if (ossIds.isEmpty()) {
            return;
        }
        Map<String, String> urlMap = imageUrlResolver.batchUrl(ossIds);
        for (MarketPlanVo vo : rows) {
            if (StrUtil.isNotBlank(vo.getCropImage())) {
                vo.setCropImageUrl(urlMap.get(vo.getCropImage().trim()));
            }
        }
    }

    /**
     * 现算每行的上市状态（V6-R158）：状态码给前端查 i18n，中文名给导出直接写 Excel。
     *
     * <p>整批共用同一个 {@code today}，避免同一次查询里跨零点导致相邻行按不同「当天」判定。
     * 日期串来自 SQL 的 {@code DATE_FORMAT(..., '%Y-%m-%d')}，为空表示该计划没排采摘明细，状态留空。</p>
     *
     * @param rows 待填充行（可空）
     */
    private void fillMarketStatus(List<MarketPlanVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        for (MarketPlanVo vo : rows) {
            String status = resolveStatus(vo, today);
            vo.setMarketStatus(status);
            vo.setMarketStatusName(MarketStatusLabel.name(status));
        }
    }

    /**
     * 单行现算状态码：上市 / 下架日期串 → {@link DateWindowStatusCalculator}。
     *
     * @param vo    聚合行
     * @param today 当天（整批共用一个，见 {@link #fillMarketStatus}）
     * @return 状态码，或 {@code null}（上市日期缺失 / 不可解析）
     */
    private String resolveStatus(MarketPlanVo vo, LocalDate today) {
        return DateWindowStatusCalculator.resolve(
            parseDate(vo.getMarketBeginDate()), parseDate(vo.getMarketEndDate()), today);
    }

    /**
     * {@code yyyy-MM-dd} 串转 {@link LocalDate}；空串 / null / 非法值一律返回 null（状态随之留空，不抛）。
     *
     * @param text 日期串
     * @return 日期，或 null
     */
    private LocalDate parseDate(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (RuntimeException e) {
            log.warn("[MarketPlan] 日期串无法解析，状态留空：{}", text);
            return null;
        }
    }

    /**
     * 取当前租户；V1 单租户场景或异常时回退 {@value #DEFAULT_TENANT}。
     *
     * @return 当前租户 ID
     */
    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return t == null || t.isEmpty() ? DEFAULT_TENANT : t;
        } catch (Exception e) {
            log.warn("[MarketPlan] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }
}
