package org.dromara.djs.breed.core.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.BoarMatingCountVo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.domain.vo.PigEarTagMapVo;
import org.dromara.djs.breed.core.domain.vo.PigLastFarrowVo;
import org.dromara.djs.breed.core.domain.vo.PigLitterAggVo;
import org.dromara.djs.breed.core.domain.vo.PigVo;

import java.util.Collection;
import java.util.List;

/**
 * 猪只信息 Mapper（BRD-CORE-001）。
 */
public interface PigMapper extends BaseMapperPlus<Pig, PigVo> {

    /**
     * 取同<b>出生日段</b>（{@code yyMMdd}，如 {@code 260508}）现存耳号的最大序号（ADR-0011 序号源 DB max，
     * R47：按出生日全场递增，不分品系品种）。
     *
     * <p>序号 = 耳号末段（{@code 品系-品种2-yyMMdd6-序号3} 的第 4 段）。SQL 用 {@code REGEXP} 精确匹配
     * 「{@code -出生日-数字结尾}」（出生日段是倒数第二段），{@code SUBSTRING_INDEX(ear_no,'-',-1)} 取末段序号，
     * {@code CAST(... AS UNSIGNED)} 转数值取 {@code MAX}。当天无现存号返回 null，调用方从 seq=1 起。
     * 旧 12 位无分隔历史号无 {@code -出生日-} 形态，天然不匹配，新旧隔离。</p>
     *
     * @param dateSeg 出生日段（{@code yyMMdd}，6 位）
     * @return 当天全场最大序号；无则 null
     */
    @Select("""
        SELECT MAX(CAST(SUBSTRING_INDEX(ear_no, '-', -1) AS UNSIGNED))
          FROM t_farm_pig_info
         WHERE del_flag = '0'
           AND ear_no REGEXP CONCAT('-', #{dateSeg}, '-[0-9]+$')
        """)
    Long selectMaxSeqByDateSegment(@Param("dateSeg") String dateSeg);

    /**
     * 探测<b>整串耳号</b>是否已被占用（UNIQUE 兜底，BRD-FIX-EARNO-001）。
     *
     * <p>引种查重的<b>主口径</b>是 {@link #existsSeqByDateSegment}（同出生日 + 后三位），本方法只作兜底：
     * 补上历史 12 位无分隔异形号等 REGEXP 匹配不到、但整串确实相同的情形。</p>
     *
     * <p>含已软删行 —— 软删行仍占 UNIQUE 中的 ear_no 列。</p>
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
     * 探测<b>同出生日下这个序号（耳号后三位）</b>是否已被占用 —— 外部引种查重的<b>主口径</b>。
     *
     * <p>甲方 2026-08-04 原话（V6 生产紧急表 row3）：「判断输入的后三位<b>在出生日期的判定里</b>是否存在重复，
     * 如果存在，则提示耳号已存在。如果不存在，就允许录入。<b>并不一定要大于才能录入</b>」。
     * 即：判重按「出生日 + 后三位」，<b>不分品系 / 品种 / 性别</b>；序号可以比后台预填号小，不重就放行。</p>
     *
     * <p>范围与 {@link #selectMaxSeqByDateSegment} 取号范围<b>同宽</b>（同 REGEXP 匹配、同 SUBSTRING_INDEX 取末段），
     * 只是把 MAX 换成等值命中 —— 取号避开的和查重看的必须是同一批号，否则仍会撞。</p>
     *
     * <p><b>放弃了什么（Kevin 2026-08-05 拍板，明确取舍）</b>：甲方历史数据里同日同序号分属不同前缀是存在的
     * （《猪只核对表》实测 47 组，如 {@code 01-01-1-260224-001} / {@code 01-02-1-260224-001} 并存）。
     * 采用本口径后，<b>今后</b>再按「不同前缀各自复用同一序号」的方式录入会被判为重号拦下；
     * 已入库的历史数据不受影响（只拦新录入）。这是按甲方最新表态取舍的结果，不是遗漏。</p>
     *
     * <p>{@code del_flag='0'}：与取号口径一致（软删猪的号可被重新使用），否则查重比取号严，
     * 会出现「后台预填的号自己都过不了查重」。整串耳号维度的兜底见 {@link #existsEarNo}。</p>
     *
     * @param dateSeg 出生日段（{@code yyMMdd}，6 位）
     * @param seq     3 位序号的数值
     * @return 命中的 id；未占用返回 null
     */
    @Select("""
        SELECT id
          FROM t_farm_pig_info
         WHERE del_flag = '0'
           AND ear_no REGEXP CONCAT('-', #{dateSeg}, '-[0-9]+$')
           AND CAST(SUBSTRING_INDEX(ear_no, '-', -1) AS UNSIGNED) = #{seq}
         LIMIT 1
        """)
    Long existsSeqByDateSegment(@Param("dateSeg") String dateSeg, @Param("seq") long seq);

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
     * 分页查「可追溯猪只」列表（STORE-TRACE-ONSITE-001 门店现场生码选猪）。
     *
     * <p>语义区别于 {@link #selectAvailableForOutboundPage}：现场生码是<b>对已出栏猪只追溯</b>，
     * 故<b>不</b>过滤 {@code current_status != 'END'}、<b>不</b>过滤需求占用——已上市 / 已终止的猪
     * 同样可被门店现场扫码追溯。仅保留 {@code pig_type='fattening'}（育肥商品猪）+ {@code del_flag='0'}。</p>
     *
     * <p>展示字段与 {@link #selectAvailableForOutboundPage} 完全一致（earNo / pigSex / pigBreedLabel /
     * ageDays），{@code lastBackfat} 复用同子查询（门店选猪卡不显但 VO 复用不另立）。
     * 排序 {@code ear_no DESC}（新出栏排前）。租户显式 {@code tenant_id='1001'}（V1 单租户）。</p>
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
          AND p.del_flag = '0'
          AND p.tenant_id = '1001'
        ORDER BY p.ear_no DESC
        """)
    IPage<PigAvailableVo> selectTraceablePigPage(IPage<PigAvailableVo> page);

    /**
     * 按耳号批量查猪只展示信息（earNo / pigSex / pigBreedLabel / ageDays），门店追溯白条 enrich 用（FIX-STORE-TRACE-BAR-001）。
     *
     * <p>additive 跨域只读方法：门店「猪肉追溯码管理」picker 改为「当天确认收货白条」口径后，
     * 先按 {@code t_warehouse_bar_info} 过滤出白条，再按白条耳号批量 enrich 猪只信息。
     * <b>不复用也不改动</b> {@link #selectTraceablePigPage}（共享 mapper 保持原样，避免跨域污染）。</p>
     *
     * <p>SELECT / 品种 label / 日龄口径与 {@link #selectTraceablePigPage} 完全一致；区别：
     * 按 {@code ear_no IN} 过滤、不限 {@code pig_type='fattening'}（白条来源猪只类型不限）、返 List 非分页。
     * 租户显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户）。耳号集合为空由 service 端短路，不传空。</p>
     *
     * @param earNos 耳号集合（已 dedupe，非空）
     * @return 命中的猪只信息行（earNo 未命中档案的白条不返回，service 端按 earNo map 回填，取不到留 null）
     */
    @Select("""
        <script>
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
            CASE WHEN p.birth_date IS NULL THEN NULL ELSE DATEDIFF(CURDATE(), p.birth_date) END AS ageDays
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
        WHERE p.del_flag = '0'
          AND p.tenant_id = '1001'
          AND p.ear_no IN
        <foreach collection='earNos' item='en' open='(' separator=',' close=')'>#{en}</foreach>
        </script>
        """)
    List<PigAvailableVo> selectPigInfoByEarNos(@Param("earNos") Collection<String> earNos);

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

    /**
     * 批量聚合母猪集合各自的「活仔数」（母猪列表列，避免 N+1）。
     *
     * <p>口径：{@code SUM(live_born)} —— 只算活着出生的（不含死胎 {@code dead_born} /
     * 木乃伊 {@code mummy_born}），与 Kevin 拍板「活仔=活口径」一致。
     * {@code GROUP BY pig_id}、只算未软删行、显式 {@code tenant_id='1001'}（V1 单租户）。
     * 空集合由调用方短路，不入 SQL；无分娩记录的母猪不在结果里（service 端回填 null）。</p>
     *
     * @param pigIds 候选母猪 id 集合（非空）
     * @return 每头有分娩记录的母猪一行 {@code (pigId, total=SUM(live_born))}
     */
    @Select("""
        <script>
        SELECT pig_id AS pigId, COALESCE(SUM(live_born), 0) AS total
          FROM t_farm_pig_farrow
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND pig_id IN
           <foreach collection="pigIds" item="id" open="(" separator="," close=")">#{id}</foreach>
         GROUP BY pig_id
        </script>
        """)
    List<PigLitterAggVo> sumLiveBornByPigIds(@Param("pigIds") Collection<Long> pigIds);

    /**
     * 批量聚合母猪集合各自的「断奶仔猪数」（母猪列表列，避免 N+1）。
     *
     * <p>口径：{@code SUM(weaned_count)} —— 所有断奶记录的当前活仔数之和。
     * {@code GROUP BY pig_id}、只算未软删行、显式 {@code tenant_id='1001'}（V1 单租户）。
     * 空集合由调用方短路，不入 SQL；无断奶记录的母猪不在结果里（service 端回填 null）。</p>
     *
     * @param pigIds 候选母猪 id 集合（非空）
     * @return 每头有断奶记录的母猪一行 {@code (pigId, total=SUM(weaned_count))}
     */
    @Select("""
        <script>
        SELECT pig_id AS pigId, COALESCE(SUM(weaned_count), 0) AS total
          FROM t_farm_pig_weaning
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND pig_id IN
           <foreach collection="pigIds" item="id" open="(" separator="," close=")">#{id}</foreach>
         GROUP BY pig_id
        </script>
        """)
    List<PigLitterAggVo> sumWeanedByPigIds(@Param("pigIds") Collection<Long> pigIds);

    /**
     * 批量聚合给定公猪耳号集合各自的「配种次数」（公猪列表 matingCount 真实统计，避免 N+1）。
     *
     * <p>配种表关联公猪用耳号（{@code t_farm_pig_breeding.boar_ear_no}，本场公猪配种时填），
     * 故按 {@code boar_ear_no} {@code COUNT(*) GROUP BY boar_ear_no}。排除已软删配种记录、
     * 排除空耳号（精液配种 boar_ear_no 为空，不计入任何公猪）；显式 {@code tenant_id='1001'}
     * （V1 单租户）。空集合由调用方短路，不入 SQL。</p>
     *
     * @param boarEarNos 公猪耳号简版集合（非空）
     * @return 每个有配种记录的公猪耳号一行 {@code (boarEarNo, total=COUNT(*))}
     */
    @Select("""
        <script>
        SELECT boar_ear_no AS boarEarNo, COUNT(*) AS total
          FROM t_farm_pig_breeding
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND boar_ear_no IS NOT NULL
           AND boar_ear_no != ''
           AND boar_ear_no IN
           <foreach collection="boarEarNos" item="en" open="(" separator="," close=")">#{en}</foreach>
         GROUP BY boar_ear_no
        </script>
        """)
    List<BoarMatingCountVo> countBreedingByBoarEarNos(@Param("boarEarNos") Collection<String> boarEarNos);

    /**
     * 按耳号简版批量查全版耳号 {@code (ear_no, ear_tag)}（详情父系/母系显示全版耳号 enrich，避免 N+1）。
     *
     * <p>{@code motherEar / fatherEar} 存的是短号，详情要显示全版 → 按短号 IN 批量反查 ear_tag。
     * 同一短号可能因耳号复用对应多 lifecycle 行，取 {@code id DESC} 最新一条（活的优先）；
     * 只算未软删行、显式 {@code tenant_id='1001'}（V1 单租户）。空集合由调用方短路。</p>
     *
     * @param earNos 耳号简版集合（非空，已 dedupe）
     * @return 命中行 {@code (earNo, earTag)}；未命中的短号不返回（service 端回填 null）
     */
    @Select("""
        <script>
        SELECT t.ear_no AS earNo, t.ear_tag AS earTag
          FROM t_farm_pig_info t
          JOIN (
                SELECT ear_no, MAX(id) AS max_id
                  FROM t_farm_pig_info
                 WHERE del_flag = '0'
                   AND tenant_id = '1001'
                   AND ear_no IN
                   <foreach collection="earNos" item="en" open="(" separator="," close=")">#{en}</foreach>
                 GROUP BY ear_no
          ) m ON m.max_id = t.id
        </script>
        """)
    List<PigEarTagMapVo> selectEarTagByEarNos(@Param("earNos") Collection<String> earNos);

    /**
     * 断奶称重 hook：把某次断奶逐头明细的个体断奶重 + 断奶日回写到对应育肥猪 pig_info 快照
     * （BRD-STAT-FIX-001，育肥猪出栏净增重溯源）。
     *
     * <p>溯源：断奶逐头明细 {@code t_farm_pig_weaning_detail.ear_no}（仔猪耳号）→ 仔猪打标
     * {@code t_farm_pig_pigletno.piglet_ear_no} → {@code pigletno.pig_id}（贴标后翻成的育肥猪 pig_info.id）。
     * 把 detail.weight 写入 pig_info.wean_weight，断奶主表 weaning_date 写入 wean_date。
     * 显式带 {@code tenant_id} JOIN 条件保证租户隔离；仅未软删行。
     * 断奶事务内联调用，与断奶主记录同生共死。该窝仔猪尚未建 pig_info 行（pig_id 为空）的不在更新集。</p>
     *
     * @param tenantId  租户
     * @param weaningId 断奶记录 id
     * @return 实际更新的育肥猪行数
     */
    @Update("""
        UPDATE t_farm_pig_info p
          JOIN t_farm_pig_pigletno pl
            ON pl.pig_id = p.id AND pl.del_flag = '0' AND pl.tenant_id = p.tenant_id
          JOIN t_farm_pig_weaning_detail wd
            ON wd.ear_no = pl.piglet_ear_no AND wd.del_flag = '0' AND wd.tenant_id = p.tenant_id
          JOIN t_farm_pig_weaning w
            ON w.id = wd.weaning_id AND w.del_flag = '0' AND w.tenant_id = p.tenant_id
           SET p.wean_weight = wd.weight,
               p.wean_date   = DATE(w.weaning_date)
         WHERE p.del_flag = '0'
           AND p.tenant_id = #{tenantId}
           AND wd.weaning_id = #{weaningId}
        """)
    int updateWeanSnapshotByWeaningId(@Param("tenantId") String tenantId,
                                      @Param("weaningId") Long weaningId);
}
