package org.dromara.djs.plant.market.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.market.domain.query.MarketPlanQuery;
import org.dromara.djs.plant.market.domain.vo.MarketPlanVo;

import java.util.List;

/**
 * 果蔬上市计划聚合 Mapper（V6-R151）。
 *
 * <p>不绑单表实体、不继承 {@code BaseMapperPlus}：本页是 {@code t_plant_plant_plan} ×
 * {@code t_plant_plant_details} × {@code t_plant_crop_info} 的跨表只读聚合。</p>
 *
 * <h3>行粒度</h3>
 * 一行 = 一条种植计划（甲方：「按照种植计划进行展示」）。明细聚合走不相关派生表 {@code agg}
 * 先按 {@code plant_id} GROUP BY 再 LEFT JOIN，避免直接 JOIN 明细造成扇出重复计数。
 *
 * <h3>LEFT JOIN 而非 INNER JOIN</h3>
 * 一次采摘明细都没有的计划（{@code agg} 整行 NULL）照样出现在列表，两个月份显 {@code -}、
 * 产量兜 0 —— 运营要看得到「还没排采摘计划」的计划才知道该催谁。排序键
 * {@code (agg.market_begin IS NULL) ASC} 把这些行显式压到最后，不依赖 MySQL 隐式 NULL 排序行为。
 *
 * <p>注意：一旦按上市/下市月份筛选，这些 NULL 行必然被滤掉（NULL 匹配不上任何月份），
 * 这是筛选语义的必然结果，不是 bug。</p>
 *
 * <h3>预计产量口径</h3>
 * row267：{@code SUM(GREATEST(明细 expected_yield − 该(地块,作物)灾害损失, 0))}，
 * 与种植计划页 / 种植总览页 / 门店需求页三处完全一致。灾害损失先按 {@code (plot_id, crop_id)}
 * 预聚合再左联，避免与明细多行左联扇出。
 *
 * <p>MP 多租户拦截器不注入 {@code @Select} 原生 SQL，故全部条件显式带 {@code #{tenantId}}。</p>
 *
 * @author djs
 */
@Mapper
public interface MarketPlanMapper {

    /** 列表 / 导出共用的 SQL 主体（分页版与全量版仅差 MP 注入的 LIMIT）。 */
    String MARKET_PLAN_SQL = """
        <script>
        SELECT p.id                                            AS planId,
               p.plan_no                                       AS planNo,
               p.plan_year                                      AS planYear,
               p.crop_id                                        AS cropId,
               c.crop_name                                      AS cropName,
               COALESCE(NULLIF(TRIM(c.image_oss_id), ''),
                        NULLIF(TRIM(c.crop_image_preview), ''),
                        NULLIF(TRIM(SUBSTRING_INDEX(c.crop_image_url, ',', 1)), '')) AS cropImage,
               COALESCE(agg.expected_yield, 0)                  AS expectedYield,
               COALESCE(agg.actual_yield, 0)                    AS actualYield,
               DATE_FORMAT(agg.market_begin, '%Y-%m')           AS marketBeginMonth,
               DATE_FORMAT(agg.market_end, '%Y-%m')             AS marketEndMonth
          FROM t_plant_plant_plan p
          LEFT JOIN t_plant_crop_info c
                 ON c.id = p.crop_id AND c.del_flag = '0' AND c.tenant_id = #{tenantId}
          LEFT JOIN (
                SELECT d.plant_id,
                       COALESCE(SUM(GREATEST(COALESCE(d.expected_yield, 0) - COALESCE(dl.disaster_loss, 0), 0)), 0) AS expected_yield,
                       COALESCE(SUM(d.actual_yield), 0) AS actual_yield,
                       MIN(d.earliest_harvestdate)      AS market_begin,
                       MAX(d.last_harvestdate)          AS market_end
                  FROM t_plant_plant_details d
                  LEFT JOIN (SELECT fr.plot_id, fr.crop_id, SUM(fr.loss_yield) AS disaster_loss
                               FROM t_plant_farm_records fr
                              WHERE fr.del_flag = '0' AND fr.tenant_id = #{tenantId}
                                AND fr.farm_type = 'disaster'
                                AND fr.plot_id IS NOT NULL AND fr.crop_id IS NOT NULL
                              GROUP BY fr.plot_id, fr.crop_id) dl
                    ON dl.plot_id = d.plot_id AND dl.crop_id = d.crop_id
                 WHERE d.del_flag = '0' AND d.tenant_id = #{tenantId}
                 GROUP BY d.plant_id
          ) agg ON agg.plant_id = p.id
         WHERE p.del_flag = '0' AND p.tenant_id = #{tenantId}
        <if test="q != null and q.cropName != null and q.cropName != ''">
            AND c.crop_name LIKE CONCAT('%', #{q.cropName}, '%')
        </if>
        <if test="q != null and q.marketBeginMonth != null and q.marketBeginMonth != ''">
            AND DATE_FORMAT(agg.market_begin, '%Y-%m') = #{q.marketBeginMonth}
        </if>
        <if test="q != null and q.marketEndMonth != null and q.marketEndMonth != ''">
            AND DATE_FORMAT(agg.market_end, '%Y-%m') = #{q.marketEndMonth}
        </if>
         ORDER BY (agg.market_begin IS NULL) ASC, agg.market_begin DESC, p.id DESC
        </script>
        """;

    /**
     * 分页查询果蔬上市计划（按上市月份降序，空上市月份排最后）。
     *
     * @param page     MP 分页对象（service 已清掉请求参数排序项）
     * @param tenantId 租户
     * @param q        查询条件（可空 = 不过滤）
     * @return 当前页数据
     */
    @Select(MARKET_PLAN_SQL)
    IPage<MarketPlanVo> selectMarketPlanPage(IPage<MarketPlanVo> page,
                                             @Param("tenantId") String tenantId,
                                             @Param("q") MarketPlanQuery q);

    /**
     * 全量查询果蔬上市计划（导出用，不分页，排序同分页版）。
     *
     * @param tenantId 租户
     * @param q        查询条件（可空 = 不过滤）
     * @return 全部匹配行
     */
    @Select(MARKET_PLAN_SQL)
    List<MarketPlanVo> selectMarketPlanList(@Param("tenantId") String tenantId,
                                            @Param("q") MarketPlanQuery q);
}
