package org.dromara.djs.breed.event.weaning.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.event.weaning.domain.PigWeaning;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningVo;
import org.dromara.djs.breed.event.weaning.domain.vo.UnweanedLitterRowVo;

import java.util.List;

public interface PigWeaningMapper extends BaseMapperPlus<PigWeaning, PigWeaningVo> {

    /**
     * 「这头仔猪已经断过奶了」的唯一判据（BRD-WEAN-SELECT-001）—— 一处定义，
     * 待断奶列表与提交去重守卫逐字共用。别名契约：{@code pl} = t_farm_pig_pigletno，
     * {@code cub} = 该仔猪的 t_farm_pig_info（LEFT JOIN，可为 NULL）。
     *
     * <p><b>三个信号缺一不可</b>，因为三条断奶路径留下的痕迹不一样：</p>
     * <ol>
     *   <li>本窝任一条未删断奶明细带这个耳号 —— 逐头断奶（行238）留下的痕迹；</li>
     *   <li>猪只档案已有断奶日 —— 逐头明细回写的个体快照；</li>
     *   <li>猪只档案已不是仔猪 —— <b>没有逐头明细的历史断奶记录</b>（明细表 V202606111520 上线前的遗留、
     *       以及 admin 汇总录入 / 未贴标窝的匿名铺行）只翻了 pig_type，前两个信号都抓不到它。
     *       只靠第 1 条会把这些窝当成「还没断」重新列出来，工人再勾一次就又断一遍。</li>
     * </ol>
     */
    String ALREADY_WEANED = """
         (EXISTS (
               SELECT 1
                 FROM t_farm_pig_weaning_detail wd
                 JOIN t_farm_pig_weaning w
                   ON w.id = wd.weaning_id AND w.del_flag = '0' AND w.tenant_id = wd.tenant_id
                WHERE wd.del_flag = '0'
                  AND wd.tenant_id = pl.tenant_id
                  AND wd.ear_no = pl.piglet_ear_no
                  AND w.farrow_id = pl.farrow_id)
          OR cub.wean_date IS NOT NULL
          OR (cub.pig_type IS NOT NULL AND cub.pig_type <> 'piglet'))
        """;

    /**
     * 「分娩未断奶母猪 + 其未断奶仔猪」扁平行（BRD-WEAN-SELECT-001，V6 行238 断奶仔猪选择页）。
     *
     * <p>一行 = 一头尚未断奶的已贴标仔猪，带所属窝（分娩）与母猪的冗余列。一条 SQL 拿全，
     * service 侧按 farrowId 归并成窝，避免逐窝 N+1。</p>
     *
     * <p>「未断奶」两个条件缺一不可：</p>
     * <ol>
     *   <li>没命中 {@link #ALREADY_WEANED} —— 按所选仔猪断奶后同窝可有多条断奶记录，只能逐头判定，
     *       不能按「本窝有没有断奶记录」整窝判；三个信号一起看才盖得住没有逐头明细的历史记录；</li>
     *   <li>对应猪只档案未终止 —— 死亡 / 淘汰 / 出栏的仔猪不该再出现在待断奶列表里。</li>
     * </ol>
     *
     * <p>整窝未贴标的窝没有耳号可逐头勾选，天然不在本结果集（那种窝走录入页按活产仔数铺匿名行）。</p>
     *
     * @param tenantId 租户（V1 恒 '1001'；显式传，不依赖拦截器改写裸 SQL）
     * @return 扁平行，按分娩日期倒序 + 仔猪耳号升序
     */
    @Select("""
        SELECT f.id                AS farrowId,
               f.pig_id            AS sowPigId,
               s.ear_no            AS sowEarNo,
               f.farrow_date       AS farrowDate,
               f.parity            AS parity,
               s.birth_date        AS sowBirthDate,
               s.introduce_date    AS sowIntroduceDate,
               b.barn_code         AS barnCode,
               b.barn_name         AS barnName,
               pe.pen_code         AS penCode,
               pe.pen_name         AS penName,
               pl.piglet_ear_no    AS pigletEarNo,
               pl.piglet_sex       AS pigletSex
          FROM t_farm_pig_pigletno pl
          JOIN t_farm_pig_farrow f
            ON f.id = pl.farrow_id AND f.del_flag = '0' AND f.tenant_id = pl.tenant_id
          JOIN t_farm_pig_info s
            ON s.id = f.pig_id AND s.del_flag = '0' AND s.tenant_id = pl.tenant_id
          LEFT JOIN t_farm_pig_info cub
            ON cub.id = pl.pig_id AND cub.del_flag = '0' AND cub.tenant_id = pl.tenant_id
          LEFT JOIN t_farm_barn_info b
            ON b.id = s.barn_id AND b.del_flag = '0' AND b.tenant_id = s.tenant_id
          LEFT JOIN t_farm_barn_pen pe
            ON pe.id = s.pen_id AND pe.del_flag = '0' AND pe.tenant_id = s.tenant_id
         WHERE pl.del_flag = '0'
           AND pl.tenant_id = #{tenantId}
           AND (cub.id IS NULL OR cub.current_status <> 'END')
           AND NOT """ + ALREADY_WEANED + """
         ORDER BY f.farrow_date DESC, f.id DESC, pl.piglet_ear_no ASC
        """)
    List<UnweanedLitterRowVo> selectUnweanedLitterRows(@Param("tenantId") String tenantId);

    /**
     * 本窝里「这批耳号中哪几头已经断过奶」（BRD-WEAN-SELECT-001 提交去重守卫）。
     *
     * <p>改造前 {@code (DN, WEAN)} 不在状态机 transition 表里，第二次提交同一窝会被非法流转直接挡住，
     * 顺带也挡住了「同一头仔猪断两次」。分批补断要求跳过状态机之后这道天然屏障就没了 ——
     * 重复断一头会多出一行明细、覆盖掉个体断奶重，还会吃掉头数守恒的预算把整窝永久卡死
     * （幽灵头数占满 live_born，真实剩下的仔猪再也断不掉）。{@code @RepeatSubmit} 只挡完全相同的
     * 请求体，改一个重量就绕过去了，拦不住这个。</p>
     *
     * <p>判据与待断奶列表共用 {@link #ALREADY_WEANED}，两边不会各自漂移。</p>
     *
     * @param tenantId 租户
     * @param farrowId 本窝分娩 ID
     * @param earNos   本次要断的仔猪耳号（非空）
     * @return 其中已断过奶的耳号；空集合表示这批都还没断
     */
    @Select("""
        <script>
        SELECT pl.piglet_ear_no
          FROM t_farm_pig_pigletno pl
          LEFT JOIN t_farm_pig_info cub
            ON cub.id = pl.pig_id AND cub.del_flag = '0' AND cub.tenant_id = pl.tenant_id
         WHERE pl.del_flag = '0'
           AND pl.tenant_id = #{tenantId}
           AND pl.farrow_id = #{farrowId}
           AND pl.piglet_ear_no IN
               <foreach item="e" collection="earNos" open="(" separator="," close=")">#{e}</foreach>
           AND """ + ALREADY_WEANED + """
        </script>
        """)
    List<String> selectAlreadyWeanedEarNos(@Param("tenantId") String tenantId,
                                           @Param("farrowId") Long farrowId,
                                           @Param("earNos") java.util.Collection<String> earNos);
}
