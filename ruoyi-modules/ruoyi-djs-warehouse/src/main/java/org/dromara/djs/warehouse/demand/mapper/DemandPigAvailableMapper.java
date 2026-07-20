package org.dromara.djs.warehouse.demand.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;

/**
 * 需求域「指定猪只」可出栏查询 Mapper（按养殖出栏日龄配置过滤）。
 *
 * <p>背景（测试 row48）：admin「指定猪只」弹框原直接调 breed 域
 * {@code IPigQueryService.listAvailableForOutbound}，该查询<b>不</b>按出栏日龄阈值过滤，
 * 列出全部育肥活体猪。需求要求只列「满足养殖后台出栏日龄配置（{@code slaughter_age_days}）」的到龄猪。</p>
 *
 * <p>过滤口径与 breed {@code selectAvailableForOutboundPage} 完全一致
 * （{@code pig_type='fattening'} + {@code current_status != 'END'} + 未被非取消态需求占用），
 * 在此基础上追加日龄阈值过滤；阈值由 service 从 {@code IProductionCycleConfigService.getValue("slaughter_age_days")}
 * 取（custom 优先，无则 default 175），与 mp 出栏选猪 {@code /search?minAgeDays=} 同语义。</p>
 *
 * <p>跨域读：SQL 仅以表名字符串引用 breed 表（{@code t_farm_pig_info / t_farm_breed_info / t_farm_pig_growth}）
 * 与需求子表 {@code t_warehouse_demand_*}，不 import breed 域 Java mapper，避免双向编译依赖
 * （与 breed 自有 mapper 跨读 demand 表同范式）。返回类型复用 breed {@link PigAvailableVo}。</p>
 *
 * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户）。</p>
 *
 * @author djs
 * @since test/d6-23 row48
 */
@Mapper
public interface DemandPigAvailableMapper {

    /**
     * 「可出栏」育肥猪分页（按出栏日龄阈值过滤）。
     *
     * <p>{@code ageDays = DATEDIFF(CURDATE(), birth_date)}；仅保留
     * {@code birth_date IS NOT NULL AND DATEDIFF(...) >= minAgeDays} 的到龄猪
     * （无生日的猪算不到龄，剔除，与 mp minAgeDays 口径一致）。排序按 ageDays DESC（老龄优先）。</p>
     *
     * @param page       分页对象
     * @param minAgeDays 出栏日龄阈值（天，service 传入；≥ 此值才返回）
     * @return 到龄可出栏育肥猪分页
     */
    @Select("""
        SELECT
            p.ear_no AS earNo,
            p.pig_sex AS pigSex,
            CASE
                WHEN bi.breed_strain_name IS NOT NULL AND si.breed_strain_name IS NOT NULL
                    THEN CONCAT(bi.breed_strain_name, '/', si.breed_strain_name)
                WHEN bi.breed_strain_name IS NOT NULL
                    THEN bi.breed_strain_name
                WHEN p.pig_strain_code IS NOT NULL AND p.pig_breed_code IS NOT NULL
                    THEN CONCAT(p.pig_breed_code, '/', p.pig_strain_code)
                ELSE COALESCE(p.pig_breed_code, p.pig_strain_code)
            END AS pigBreedLabel,
            CASE WHEN p.birth_date IS NULL THEN NULL ELSE DATEDIFF(CURDATE(), p.birth_date) END AS ageDays,
            (SELECT g.backfat_thickness
               FROM t_farm_pig_growth g
              WHERE g.pig_id = p.id
                AND g.del_flag = '0'
                AND g.backfat_thickness IS NOT NULL
              ORDER BY g.measure_date DESC, g.id DESC
              LIMIT 1) AS lastBackfat
        FROM t_farm_pig_info p
        LEFT JOIN t_farm_breed_info bi
               ON bi.breed_strain_code = p.pig_breed_code
              AND bi.breed_strain = 1
              AND bi.del_flag = '0'
              AND bi.tenant_id = p.tenant_id
        LEFT JOIN t_farm_breed_info si
               ON si.breed_strain_code = p.pig_strain_code
              AND si.breed_strain = 2
              AND si.del_flag = '0'
              AND si.tenant_id = p.tenant_id
        WHERE p.pig_type = 'fattening'
          AND p.current_status <> 'END'
          AND p.del_flag = '0'
          AND p.tenant_id = '1001'
          AND p.birth_date IS NOT NULL
          AND DATEDIFF(CURDATE(), p.birth_date) >= #{minAgeDays}
          AND NOT EXISTS (
                SELECT 1
                  FROM t_warehouse_demand_pig dp
                  JOIN t_warehouse_demand_manage dm ON dm.id = dp.demand_id
                 WHERE dp.ear_no = p.ear_no
                   AND dp.del_flag = '0'
                   AND dm.del_flag = '0'
                   AND dm.demand_status <> 'CANCELLED'
                   AND dm.tenant_id = p.tenant_id
          )
        ORDER BY ageDays DESC, p.ear_no ASC
        """)
    IPage<PigAvailableVo> selectAvailableForOutboundPage(IPage<PigAvailableVo> page,
                                                         @Param("minAgeDays") Integer minAgeDays);

    /**
     * 「可出栏」到龄育肥猪头数 COUNT（SummaryBar「可出栏猪只头数」口径与列表一致）。
     *
     * <p>过滤条件与 {@link #selectAvailableForOutboundPage} 完全一致（含日龄阈值）。</p>
     *
     * @param minAgeDays 出栏日龄阈值（天）
     * @return 到龄可出栏育肥猪头数
     */
    @Select("""
        SELECT COUNT(1)
          FROM t_farm_pig_info p
         WHERE p.pig_type = 'fattening'
           AND p.current_status <> 'END'
           AND p.del_flag = '0'
           AND p.tenant_id = '1001'
           AND p.birth_date IS NOT NULL
           AND DATEDIFF(CURDATE(), p.birth_date) >= #{minAgeDays}
           AND NOT EXISTS (
                 SELECT 1
                   FROM t_warehouse_demand_pig dp
                   JOIN t_warehouse_demand_manage dm ON dm.id = dp.demand_id
                  WHERE dp.ear_no = p.ear_no
                    AND dp.del_flag = '0'
                    AND dm.del_flag = '0'
                    AND dm.demand_status <> 'CANCELLED'
                    AND dm.tenant_id = p.tenant_id
           )
        """)
    int countAvailableForOutbound(@Param("minAgeDays") Integer minAgeDays);
}
