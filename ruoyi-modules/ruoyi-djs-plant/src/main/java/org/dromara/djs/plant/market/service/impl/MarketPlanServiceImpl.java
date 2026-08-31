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
import org.dromara.djs.plant.market.domain.query.MarketPlanQuery;
import org.dromara.djs.plant.market.domain.vo.MarketPlanVo;
import org.dromara.djs.plant.market.mapper.MarketPlanMapper;
import org.dromara.djs.plant.market.service.IMarketPlanService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 果蔬上市计划实现（V6-R151）。
 *
 * <p>纯只读聚合 service（非 {@code DjsBaseServiceImpl}，无 CRUD / softDelete）：
 * 取聚合行 → 批量解析作物图 URL → 返回。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPlanServiceImpl implements IMarketPlanService {

    private static final String DEFAULT_TENANT = "1001";

    /** 导出行数软上限：超过只告警不截断（与 overview 导出的 5000 行约定对齐）。 */
    private static final int EXPORT_WARN_ROWS = 5000;

    private final MarketPlanMapper marketPlanMapper;

    private final ImageUrlResolver imageUrlResolver;

    @Override
    public TableDataInfo<MarketPlanVo> queryPageList(MarketPlanQuery query, PageQuery pageQuery) {
        // PageQuery 构造参数顺序为 (pageSize, pageNum)
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(10, 1);
        IPage<MarketPlanVo> page = marketPlanMapper.selectMarketPlanPage(buildFixedOrderPage(pq), currentTenant(), query);
        fillImageUrl(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<MarketPlanVo> queryList(MarketPlanQuery query) {
        List<MarketPlanVo> list = marketPlanMapper.selectMarketPlanList(currentTenant(), query);
        if (list == null) {
            return List.of();
        }
        if (list.size() > EXPORT_WARN_ROWS) {
            log.warn("[MarketPlan] 导出行数 {} 超过软上限 {}，未截断，注意 Excel 生成耗时", list.size(), EXPORT_WARN_ROWS);
        }
        fillImageUrl(list);
        return list;
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
