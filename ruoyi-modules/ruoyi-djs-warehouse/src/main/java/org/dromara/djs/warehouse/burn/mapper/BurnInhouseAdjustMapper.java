package org.dromara.djs.warehouse.burn.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.djs.warehouse.burn.domain.vo.BurnInhouseAdjustVo;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 燎毛间产品重量调整 Mapper（V6-R43）。
 *
 * <p>本 Mapper 不绑实体（只读列表 + 一条定向 UPDATE），故不继承 {@code BaseMapperPlus}。</p>
 *
 * @author djs
 * @since V6-R43
 */
public interface BurnInhouseAdjustMapper {

    /**
     * 「燎毛间入库产出行」的结构判据（本页数据范围的唯一定义）。
     *
     * <p>{@code t_warehouse_product_inhouse} 有三类写入方：燎毛间入库、物资/白条领用产生的在制品（WIP）、
     * 门店拆单。三者靠这三个列区分：</p>
     * <ul>
     *   <li>{@code white_bar_id IS NOT NULL} —— 领用 WIP 行只带 {@code white_bar_no} 不带 {@code white_bar_id}</li>
     *   <li>{@code material_id IS NULL} —— 领用 WIP 行的 {@code material_id} 恒非空（原材料 = 自身）</li>
     *   <li>{@code source = 'warehouse'} —— 排除门店拆单行（{@code source='store'}）</li>
     * </ul>
     *
     * <p>不用 {@code burn_record_id IS NOT NULL} 当判据：那一列是 V202608300500 才加的，
     * 历史行靠迁移回填，万一某行没回填上就会从这张列表里静默消失。</p>
     */
    String BURN_ROW_PREDICATE =
        " ih.white_bar_id IS NOT NULL AND ih.material_id IS NULL AND ih.source = 'warehouse' ";

    /**
     * 列表分页查询。
     *
     * <p>{@code inboundedWeight} 用相关子查询而非 JOIN 派生表：相关子查询只对当前页那几行求值，
     * 且走 {@code idx_white_bar(tenant_id, white_bar_id)}；派生表要先把整张 inhouse 聚合一遍。</p>
     *
     * <p>{@code tenant_id = '1001'} 显式写死：V1 单租户，原生 SQL 不走 MP 租户拦截器注入
     *（与 {@code VegOutMapper} / {@code BarInfoMapper} 同口径）。</p>
     */
    @Select("""
        <script>
        SELECT ih.id                       AS id,
               ih.produce_time             AS inboundTime,
               ih.product_name             AS productName,
               ih.product_weight           AS productWeight,
               ih.product_unit             AS productUnit,
               ih.ear_no                   AS earNo,
               b.status                    AS barStatus,
               CASE WHEN b.status IN ('pending_singe','singing') THEN 0 ELSE 1 END AS burnFinished,
               l.location_name             AS locationName,
               ih.is_adjusted              AS isAdjusted,
               br.operator_id              AS operatorId,
               ih.adjust_time              AS adjustTime,
               ih.adjust_by                AS adjustBy,
               b.arrive_weight             AS arriveWeight,
               (SELECT COALESCE(SUM(x.product_weight), 0)
                  FROM t_warehouse_product_inhouse x
                 WHERE x.tenant_id = '1001' AND x.del_flag = '0'
                   AND x.white_bar_id = ih.white_bar_id
                   AND x.material_id IS NULL
                   AND x.source = 'warehouse')  AS inboundedWeight
          FROM t_warehouse_product_inhouse ih
          LEFT JOIN t_warehouse_bar_info b         ON b.id  = ih.white_bar_id    AND b.del_flag = '0'
          LEFT JOIN t_warehouse_location_info l    ON l.id  = ih.location_id     AND l.del_flag = '0'
          LEFT JOIN t_warehouse_pig_burn_record br ON br.id = ih.burn_record_id  AND br.del_flag = '0'
         WHERE ih.tenant_id = '1001'
           AND ih.del_flag = '0'
           AND""" + BURN_ROW_PREDICATE + """
        <if test="dateFrom != null">
           AND ih.produce_time &gt;= #{dateFrom}
        </if>
        <if test="dateTo != null">
           AND ih.produce_time &lt; #{dateTo}
        </if>
        <if test="productName != null and productName != ''">
           AND ih.product_name LIKE CONCAT('%', #{productName}, '%')
        </if>
        <if test="isAdjusted != null">
           AND ih.is_adjusted = #{isAdjusted}
        </if>
         ORDER BY ih.produce_time DESC, ih.id DESC
        </script>
        """)
    IPage<BurnInhouseAdjustVo> selectAdjustPage(IPage<BurnInhouseAdjustVo> page,
                                                @Param("dateFrom") Date dateFrom,
                                                @Param("dateTo") Date dateTo,
                                                @Param("productName") String productName,
                                                @Param("isAdjusted") Integer isAdjusted);

    /**
     * 改写产出行的入库重量并打上调整留痕。
     *
     * <p><b>覆盖 {@code product_weight}</b>：这一列就是「该产品这次入了多少」，只有这一次入库写过它，
     * 甲方口径是「相当于产品是按修改完成后的重量入的库」，所以直接改写成新值。</p>
     *
     * <p>{@code AND product_weight = #{oldWeight}} 是行级乐观锁：service 先读旧重量算差额，
     * 若这中间有并发把重量改了，这里 affectedRows==0，调用方回滚 —— 否则差额会算错、
     * 库存与流水会被按错误的差额推。</p>
     *
     * @return affectedRows（1=成功；0=行已变化 / 已软删）
     */
    @Update("UPDATE t_warehouse_product_inhouse "
        + "   SET product_weight = #{newWeight},"
        + "       is_adjusted = 1,"
        + "       adjust_time = NOW(),"
        + "       adjust_by = #{userId},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE id = #{id} AND product_weight = #{oldWeight} AND del_flag = '0'")
    int applyAdjust(@Param("id") Long id,
                    @Param("oldWeight") BigDecimal oldWeight,
                    @Param("newWeight") BigDecimal newWeight,
                    @Param("userId") Long userId);

}
