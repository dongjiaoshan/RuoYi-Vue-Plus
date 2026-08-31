package org.dromara.djs.plant.market.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 果蔬上市计划查询条件（V6-R151，运营管理 → 农场信息 → 果蔬上市计划）。
 *
 * <p>甲方点名的三项筛选，不多不少：作物名称模糊 + 上市月份 + 下市月份。
 * 两个月份都是 {@code yyyy-MM}（前端 month picker 的 value-format 就是 YYYY-MM），
 * 与列表展示值完全同形——用户看到哪个值就能照着搜哪个值。</p>
 *
 * <p>本查询不继承 {@code BaseEntity}：本页是跨表只读聚合，没有单表 wrapper 可用，
 * 全部条件在原生 SQL 里显式拼。</p>
 *
 * @author djs
 */
@Data
public class MarketPlanQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物名称（模糊，对 t_plant_crop_info.crop_name）。 */
    private String cropName;

    /** 上市月份 yyyy-MM（= MIN(明细最早采摘日期) 所在月）。 */
    private String marketBeginMonth;

    /** 下市月份 yyyy-MM（= MAX(明细最晚采摘日期) 所在月）。 */
    private String marketEndMonth;
}
