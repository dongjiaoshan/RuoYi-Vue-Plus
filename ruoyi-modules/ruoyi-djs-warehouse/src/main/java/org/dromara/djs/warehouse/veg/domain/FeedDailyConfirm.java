package org.dromara.djs.warehouse.veg.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 有机饲喂日确认实体（框数 + 确认人，一天一条）。
 *
 * <p>对应表 {@code t_warehouse_feed_daily_confirm}。与明细表 {@link FeedLog} 按 {@code feed_date} 左联：
 * feed_log 一天可有多条（毛菜间送的 + 仓库领用的），而框数是仓库对当日送达总量的**一次**人工确认，
 * 故按确认粒度独立成表，不挂明细行。</p>
 *
 * @author djs
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_feed_daily_confirm")
public class FeedDailyConfirm extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 饲喂日期（日维度，唯一键的一部分）。
     */
    private LocalDate feedDate;

    /**
     * 仓库确认框数（可小数）。
     */
    private BigDecimal boxCount;

    /**
     * 确认人 user_id（默认录入人，可改选养殖部人员）。
     */
    private Long confirmUserId;

    /**
     * 确认时间。
     */
    private Date confirmTime;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删唯一辅助列（0=有效）。让 {@code uk_feed_date} 在软删后放行同日重录；
     * 逻辑删除本身由继承来的 {@code del_flag} 负责，这里不加 {@code @TableLogic}。
     */
    private Long delUnique;
}
