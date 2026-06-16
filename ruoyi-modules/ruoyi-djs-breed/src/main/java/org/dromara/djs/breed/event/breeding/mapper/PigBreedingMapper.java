package org.dromara.djs.breed.event.breeding.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.event.breeding.domain.PigBreeding;
import org.dromara.djs.breed.event.breeding.domain.vo.PigBreedingVo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 配种记录 mapper（BRD-EVENT-002）。
 *
 * @author djs
 * @since BRD-EVENT-002
 */
public interface PigBreedingMapper extends BaseMapperPlus<PigBreeding, PigBreedingVo> {

    /**
     * 配种记录分页（FIX-BRD-MP-BREEDING-001 B4 enrich）。
     *
     * <p>裸 {@code selectVoPage} 只透传 breeding 本表字段，记录卡缺母猪实时状态/状态天数。
     * 本方法 LEFT JOIN {@code t_farm_pig_info p} → {@code p.current_status AS sowStatus}、
     * {@code DATEDIFF(NOW(), p.status_started_at) AS statusDays}（母猪当前 lifecycle + 状态天数，
     * 实时派生而非配种时快照），并 {@code DATEDIFF(NOW(), p.birth_date) AS ageDays}（母猪日龄，记录卡最后一列）。
     * 「上一次状态」{@code previousStatus} = 该母猪状态流水 {@code t_farm_status_record} 最新一条的
     * {@code old_status}（LEFT JOIN 子查询取 MAX(id) 那条）：即母猪进入当前状态之前所处的状态 code，
     * 前端按 lifecycle 字典翻中文；无状态流水 → null。配种人员姓名由 VO 上
     * {@code @Translation(USER_ID_TO_NICKNAME)} 按 {@code operatorId} 序列化时翻译，本 SQL 不 JOIN sys_user。</p>
     *
     * <p>SQL 手写完整 {@code tenant_id = '1001'}（V1 单租户口径），用
     * {@code @InterceptorIgnore(tenantLine = "true")} 关掉 MP 租户拦截器对这条手写 LEFT JOIN 的二次注入——
     * 否则拦截器把从表 {@code p} 的租户条件推进 WHERE，令 LEFT JOIN 退化为 INNER JOIN、
     * 漏栋舍归属为空的记录。仅作用本方法，不影响 mapper 其余 CRUD 的租户隔离。</p>
     *
     * @param page          MP 分页对象（{@link IPage}，框架自动拼 LIMIT + count）
     * @param pigId         母猪 id（可空）
     * @param earNo         母猪耳号模糊匹配 LIKE %earNo%（可空，记录列表筛选用）
     * @param breedingType  配种方式 code（可空）
     * @param boarEarNo     公猪耳号精确匹配（可空）
     * @param beginAt       配种日期下界（含，可空）
     * @param endBefore     配种日期上界（不含，可空）
     * @return 富化后的配种记录分页
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT b.id, b.pig_id, b.ear_no, b.breeding_date, b.breeding_type, b.boar_ear_no,
               b.semen_code, b.parity, b.operator_id, b.barn_name, b.pen_name, b.remark, b.create_time,
               p.current_status AS sow_status,
               DATEDIFF(NOW(), p.status_started_at) AS status_days,
               DATEDIFF(NOW(), p.birth_date) AS age_days,
               sr.old_status AS previous_status
          FROM t_farm_pig_breeding b
          LEFT JOIN t_farm_pig_info p
            ON p.id = b.pig_id
           AND p.del_flag = '0'
           AND p.tenant_id = '1001'
          LEFT JOIN t_farm_status_record sr
            ON sr.id = (
                SELECT MAX(s2.id)
                  FROM t_farm_status_record s2
                 WHERE s2.pig_id = b.pig_id
                   AND s2.del_flag = '0'
                   AND s2.tenant_id = '1001'
            )
         WHERE b.del_flag = '0'
           AND b.tenant_id = '1001'
           <if test="pigId != null">AND b.pig_id = #{pigId}</if>
           <if test="earNo != null and earNo != ''">AND b.ear_no LIKE CONCAT('%', #{earNo}, '%')</if>
           <if test="breedingType != null and breedingType != ''">AND b.breeding_type = #{breedingType}</if>
           <if test="boarEarNo != null and boarEarNo != ''">AND b.boar_ear_no = #{boarEarNo}</if>
           <if test="beginAt != null">AND b.breeding_date &gt;= #{beginAt}</if>
           <if test="endBefore != null">AND b.breeding_date &lt; #{endBefore}</if>
         ORDER BY b.breeding_date DESC, b.id DESC
        </script>
        """)
    IPage<PigBreedingVo> selectBreedingVoPage(IPage<PigBreedingVo> page,
                                              @Param("pigId") Long pigId,
                                              @Param("earNo") String earNo,
                                              @Param("breedingType") String breedingType,
                                              @Param("boarEarNo") String boarEarNo,
                                              @Param("beginAt") LocalDateTime beginAt,
                                              @Param("endBefore") LocalDateTime endBefore);

    /**
     * 按公猪耳号聚合配种次数 + 最近配种日期（小程序公猪库存列表 enrich 用，row210）。
     *
     * <p>对入参 {@code boarEarNos} 集合内的每个公猪耳号，GROUP BY 统计 COUNT(*) 配种次数与
     * MAX(breeding_date) 最近配种时间。返回每行 Map：{@code boarEarNo}(String) /
     * {@code matingCount}(Long) / {@code lastMatingDate}(LocalDateTime)。空集合时上层不调用。</p>
     *
     * <p>手写 {@code tenant_id = '1001'}（V1 单租户口径），用
     * {@code @InterceptorIgnore(tenantLine = "true")} 避免 MP 租户拦截器二次注入。</p>
     *
     * @param boarEarNos 本批公猪耳号集合（非空）
     * @return 每个耳号一行聚合结果（无配种记录的耳号不出现 → 上层按缺省 0/null 兜底）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT b.boar_ear_no AS boarEarNo,
               COUNT(*) AS matingCount,
               MAX(b.breeding_date) AS lastMatingDate
          FROM t_farm_pig_breeding b
         WHERE b.del_flag = '0'
           AND b.tenant_id = '1001'
           AND b.boar_ear_no IS NOT NULL
           AND b.boar_ear_no IN
           <foreach collection="boarEarNos" item="earNo" open="(" separator="," close=")">
               #{earNo}
           </foreach>
         GROUP BY b.boar_ear_no
        </script>
        """)
    List<Map<String, Object>> aggregateMatingByBoarEarNo(@Param("boarEarNos") Collection<String> boarEarNos);
}
