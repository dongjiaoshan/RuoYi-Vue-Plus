package org.dromara.djs.store.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店看板 - 近 10 日客户增长单点（FIX-MGMT-MP-STORE-001）。
 *
 * <p>来源：{@code t_store_member} 按 {@code DATE(create_time)} 分组 COUNT，近 10 日窗口。
 * mapper 仅出"当日有新增"的日期行；前端按 10 日窗口对缺失日补 0。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-STORE-001
 */
@Data
public class StoreMemberGrowthPointVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日期（'YYYY-MM-DD' 字符串）。
     */
    private String date;

    /**
     * 当日新增会员数。
     */
    private Integer count;

}
