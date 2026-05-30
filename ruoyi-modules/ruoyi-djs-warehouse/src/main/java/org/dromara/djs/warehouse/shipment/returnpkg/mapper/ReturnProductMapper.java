package org.dromara.djs.warehouse.shipment.returnpkg.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.ReturnProduct;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnProductVo;

/**
 * 退货管理 Mapper（WMS-SHIP-001）。
 *
 * @author djs
 * @since WMS-SHIP-001
 */
public interface ReturnProductMapper extends BaseMapperPlus<ReturnProduct, ReturnProductVo> {

    /**
     * 取当日已用的 return_no 最大序号（inline 业务码生成时用）。
     *
     * <p>返回 0 表示当日还没单。</p>
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(return_no, -4) AS UNSIGNED)), 0) "
        + "  FROM t_warehouse_return_product "
        + " WHERE return_no LIKE CONCAT('RET', #{datePart}, '%') "
        + "   AND del_flag = '0'")
    int selectMaxReturnSeqByDate(@Param("datePart") String yyyyMMdd);
}
