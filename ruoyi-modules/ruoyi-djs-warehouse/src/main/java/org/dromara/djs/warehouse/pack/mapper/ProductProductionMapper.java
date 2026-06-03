package org.dromara.djs.warehouse.pack.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;

import java.util.Date;
import java.util.List;

/**
 * 发货产品生产记录 Mapper（WMS-PACK-001 + WMS-SHIP-001 合并版）。
 *
 * <p>D10 P0 hotfix：原 SHIP 包 {@code org.dromara.djs.warehouse.product.mapper.ProductProductionMapper}
 * 与本 Mapper 同短名引起 MyBatis ClassPathMapperScanner bean name 冲突，admin 拒启动。
 * 合并方案 a — 本 Mapper（PACK 包）为权威，SHIP 端方法 {@link #markDeliveryChecked} 合并进来；
 * SHIP service 改 import 本路径。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
public interface ProductProductionMapper extends BaseMapperPlus<ProductProduction, ProductProductionVo> {

    /**
     * 查询今日（按 {@code produce_no LIKE 'yyMMdd + prefix + %'}）已用最大序号。
     *
     * <p>同 burn/cut inline 范式（D8 / D9 PigBurnRecordMapper / PigCutRecordMapper）；
     * 并发安全 — service 层在事务里串行调用 + UNIQUE (tenant_id, produce_no, del_unique) 兜底；
     * 极小概率抢同一序号场景由 SQLIntegrityConstraintViolationException 触发事务回滚 + mp 端重试。</p>
     *
     * @param prefix 6 位前缀 {@code yyMMdd + 单字母前缀}，例如 {@code 260528G}（果蔬）
     */
    @Select("SELECT MAX(produce_no) FROM t_warehouse_product_production "
        + "WHERE produce_no LIKE CONCAT(#{prefix}, '%') AND del_flag = '0'")
    String selectMaxProduceNoByPrefix(@Param("prefix") String prefix);

    /**
     * 批量将一组 product_production 行从 {@code is_delivery_check=0} 推进到 1，
     * 同时写入 {@code delivery_check_time} 与 {@code demand_id}（WMS-SHIP-001 ShipmentService 调用）。
     *
     * <p>发货采用"按可用库存匹配 + 确认时绑定 demand"语义（SHIP-DEMANDID-001）：可用库存
     * {@code demand_id} 原本为 NULL（pack 链不分配），工人发货确认时由本次 demand 绑定，
     * 一次原子 UPDATE 完成"标清点 + 绑 demand"，让 {@code ShipTraceEventListener} 之后能按
     * {@code demand_id} 查到本次发货的 production。</p>
     *
     * <p>WHERE 子句额外加 {@code is_delivery_check=0} 实现乐观锁：</p>
     * <ul>
     *   <li>若返回 affectedRows &lt; ids.size() → 有并发清点冲突 → service 层抛 ServiceException</li>
     *   <li>tenant_id 由 MP 拦截器在 final SQL 阶段注入</li>
     * </ul>
     *
     * @param ids       发货产品 id 集合
     * @param checkTime 清点时间
     * @param demandId  本次发货归属的需求 ID（回写绑定）
     * @return 实际更新行数
     */
    @Update({
        "<script>",
        "UPDATE t_warehouse_product_production",
        "   SET is_delivery_check = 1,",
        "       delivery_check_time = #{checkTime},",
        "       demand_id = #{demandId}",
        " WHERE id IN",
        "   <foreach collection='ids' item='id' separator=',' open='(' close=')'>#{id}</foreach>",
        "   AND is_delivery_check = 0",
        "   AND del_flag = '0'",
        "</script>"
    })
    int markDeliveryChecked(@Param("ids") List<Long> ids,
                            @Param("checkTime") Date checkTime,
                            @Param("demandId") Long demandId);

}
