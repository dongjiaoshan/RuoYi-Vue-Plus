package org.dromara.djs.breed.core.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.domain.vo.PigLastFarrowVo;
import org.dromara.djs.breed.core.domain.vo.PigVo;

import java.util.Collection;
import java.util.List;

/**
 * 猪只信息 Mapper（BRD-CORE-001）。
 */
public interface PigMapper extends BaseMapperPlus<Pig, PigVo> {

    /**
     * 取同前缀（{@code farmCode2+barnCode2+yyMM}）现存耳号的最大值（BRD-FIX-EARNO-001 序号源 DB max）。
     *
     * <p>{@code likeRight(ear_no, prefix)} + {@code orderByDesc(ear_no)} + {@code LIMIT 1}。
     * 耳号同前缀同位数 → 字符串降序首条即数值最大。空前缀（无人引过）返回 null，调用方从 seq=1 起。</p>
     *
     * @param prefix 耳号前缀（farmCode2 + barnCode2 + yyMM）
     * @return 该前缀下最大耳号；无则 null
     */
    default String selectMaxEarNoByPrefix(String prefix) {
        LambdaQueryWrapper<Pig> wrapper = Wrappers.<Pig>lambdaQuery()
            .likeRight(Pig::getEarNo, prefix)
            .orderByDesc(Pig::getEarNo)
            .last("LIMIT 1");
        Pig pig = selectOne(wrapper);
        return pig == null ? null : pig.getEarNo();
    }

    /**
     * 探测耳号是否已被占用（BRD-FIX-EARNO-001 UNIQUE 兜底，含已软删行 —— 软删行仍占 UNIQUE 中的 ear_no 列）。
     *
     * <p>不加 {@code del_flag='0'} 过滤：UNIQUE 约束 {@code (tenant_id, ear_no, lifecycle_id, del_unique)}
     * 里软删行 del_unique=id ≠ 0，理论上不撞，但耳号语义上仍应避开历史耳号 → 探测全部行。</p>
     *
     * @param earNo 候选耳号
     * @return 命中的 id；未占用返回 null
     */
    @Select("SELECT id FROM t_farm_pig_info WHERE ear_no = #{earNo} LIMIT 1")
    Long existsEarNo(@Param("earNo") String earNo);

    /**
     * 按耳号简版查 pigId（mp 端二选一支持 — 工人不输 19 位 snowflake，直接输耳号）。
     * <p>返回 null 表示耳号不存在（被软删或未引种）。</p>
     */
    @Select("SELECT id FROM t_farm_pig_info WHERE ear_no = #{earNo} AND del_flag = '0' LIMIT 1")
    Long selectIdByEarNo(@Param("earNo") String earNo);

    /**
     * 按耳号查 current_status（跨模块只读：暴露给 {@code IPigQueryService} 用，
     * 不要在养殖域外直接 inject 本 mapper）。
     * <p>返回 null 表示耳号不存在或已软删。</p>
     */
    @Select("SELECT current_status FROM t_farm_pig_info WHERE ear_no = #{earNo} AND del_flag = '0' LIMIT 1")
    String selectCurrentStatusByEarNo(@Param("earNo") String earNo);

    /**
     * 分页查「可出栏」育肥猪列表（DJS-FIX-ADMIN-W22-001）。
     *
     * <p>过滤条件：</p>
     * <ul>
     *   <li>{@code pig_type='fattening'}（育肥猪类型）</li>
     *   <li>{@code current_status != 'END'}（尚未终止）</li>
     *   <li>{@code del_flag='0'}</li>
     *   <li>NOT EXISTS 关联到非取消态需求（防止跨需求重复指定）</li>
     * </ul>
     *
     * <p>展示字段：</p>
     * <ul>
     *   <li>{@code earNo}：耳号简版（业务键，下游 {@code assignPigs(earNos)} 直接用）</li>
     *   <li>{@code pigSex}：性别（字典 djs_pig_sex）</li>
     *   <li>{@code pigBreedLabel}：品种/品系 label，{@code CONCAT_WS('/', breed_name, strain_name)}；
     *       双 LEFT JOIN {@code t_farm_breed_info} 取 name；缺失时兜底用编码</li>
     *   <li>{@code ageDays}：{@code DATEDIFF(CURDATE(), birth_date)}</li>
     *   <li>{@code lastBackfat}：相关子查询取 measure_date DESC 第一条的 backfat_thickness</li>
     * </ul>
     *
     * <p>排序按 {@code ageDays DESC}（出栏首选老龄）。</p>
     *
     * <p>租户隔离：未启用全局拦截器，本 SQL 显式 WHERE {@code p.tenant_id = '1001'}（V1 单租户）。</p>
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
    IPage<PigAvailableVo> selectAvailableForOutboundPage(IPage<PigAvailableVo> page);

    /**
     * 批量查给定母猪集合各自的「最近一条分娩日期」（D12X-MP-FARROW-WEANING-001 断奶选猪到期窗口）。
     *
     * <p>跨表读 {@code t_farm_pig_farrow}（本 mapper 是 core 自有 mapper，仅在 SQL 字符串里引表名，
     * 不 import event 包 → 不制造 core→event 反向依赖，与 {@link #selectAvailableForOutboundPage}
     * 跨读 growth/demand 表同范式）。</p>
     *
     * <p>{@code GROUP BY pig_id} 取 {@code MAX(farrow_date)}；只算未软删行；显式 {@code tenant_id='1001'}
     * （V1 单租户，与本 mapper 其他原生 SQL 一致）。空集合由调用方短路，不入 SQL。</p>
     *
     * @param pigIds 候选母猪 id 集合（非空）
     * @return 每头母猪一行 {@code (pigId, lastFarrowDate)}；无分娩记录的母猪不在结果里
     */
    @Select("""
        <script>
        SELECT pig_id AS pigId, DATE(MAX(farrow_date)) AS lastFarrowDate
          FROM t_farm_pig_farrow
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND pig_id IN
           <foreach collection="pigIds" item="id" open="(" separator="," close=")">#{id}</foreach>
         GROUP BY pig_id
        </script>
        """)
    List<PigLastFarrowVo> selectLastFarrowDateByPigIds(@Param("pigIds") Collection<Long> pigIds);

    /**
     * 「可出栏」育肥猪头数 COUNT（DJS-FIX-ADMIN-W22-003 SummaryBar）。
     *
     * <p>过滤条件与 {@link #selectAvailableForOutboundPage} 完全一致：</p>
     * <ul>
     *   <li>{@code pig_type='fattening'} + {@code current_status != 'END'} + {@code del_flag='0'}</li>
     *   <li>NOT EXISTS 关联到非取消态需求</li>
     *   <li>显式 {@code tenant_id='1001'}（V1 单租户，与 page 方法对齐）</li>
     * </ul>
     */
    @Select("""
        SELECT COUNT(1)
          FROM t_farm_pig_info p
         WHERE p.pig_type = 'fattening'
           AND p.current_status <> 'END'
           AND p.del_flag = '0'
           AND p.tenant_id = '1001'
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
    int countAvailableForOutbound();
}
