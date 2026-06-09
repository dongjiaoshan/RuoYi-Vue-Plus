package org.dromara.djs.warehouse.pigbuy.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.pigbuy.domain.PigPurchase;
import org.dromara.djs.warehouse.pigbuy.domain.vo.PigPurchaseVo;

/**
 * 外购猪只到货登记 Mapper（FIX-WMS-MP-PIGBUY-001）。
 *
 * @author djs
 * @since FIX-WMS-MP-PIGBUY-001
 */
public interface PigPurchaseMapper extends BaseMapperPlus<PigPurchase, PigPurchaseVo> {

    /**
     * 查询今日（{@code purchase_no LIKE 'PBUY{yyMMdd}%'}）已用最大业务码。
     *
     * <p>用于 inline 生成 {@code purchaseNo}（{@code PBUY+YYMMDD+4 位}）：返回 null 时下一序号 = 1；
     * 返回 'PBUY2606200003' 时下一序号 = 4。</p>
     *
     * <p>并发安全：service 在事务里串行调用 + UNIQUE (tenant_id, purchase_no, del_unique) 兜底；
     * 极小概率并发抢同号由 SQLIntegrityConstraintViolationException 触发事务回滚 + mp 端重试。</p>
     */
    @Select("SELECT MAX(purchase_no) FROM t_warehouse_pig_purchase "
        + "WHERE purchase_no LIKE CONCAT('PBUY', #{yyMMdd}, '%') AND del_flag = '0'")
    String selectMaxPurchaseNoByDate(@Param("yyMMdd") String yyMMdd);

}
