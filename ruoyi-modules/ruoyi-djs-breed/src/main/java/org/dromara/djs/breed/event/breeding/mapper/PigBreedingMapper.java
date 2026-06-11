package org.dromara.djs.breed.event.breeding.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.event.breeding.domain.PigBreeding;
import org.dromara.djs.breed.event.breeding.domain.vo.PigBreedingVo;

import java.time.LocalDateTime;

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
     * 实时派生而非配种时快照）。配种人员姓名由 VO 上 {@code @Translation(USER_ID_TO_NICKNAME)} 按
     * {@code operatorId} 序列化时翻译，本 SQL 不 JOIN sys_user。</p>
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
               DATEDIFF(NOW(), p.status_started_at) AS status_days
          FROM t_farm_pig_breeding b
          LEFT JOIN t_farm_pig_info p
            ON p.id = b.pig_id
           AND p.del_flag = '0'
           AND p.tenant_id = '1001'
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
}
