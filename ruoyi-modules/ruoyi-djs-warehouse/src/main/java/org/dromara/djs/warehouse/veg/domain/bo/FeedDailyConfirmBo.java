package org.dromara.djs.warehouse.veg.domain.bo;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 有机饲喂日确认录入入参（mp 行268【框数录入】）。
 *
 * @author djs
 */
@Data
public class FeedDailyConfirmBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 饲喂日期（日维度，upsert 键）。
     */
    @NotNull(message = "饲喂日期不能为空")
    private LocalDate feedDate;

    /**
     * 框数（必填，正数，最多 1 位小数）。
     *
     * <p>甲方 row98：框可以装不满，要能录半框，故放开到 1 位小数（原来只收整数）。
     * 落库列 {@code DECIMAL(10,2)} 本就存得下，小数位约束只在校验层收口，不需要改表。</p>
     */
    @NotNull(message = "框数不能为空")
    @Positive(message = "框数必须大于 0")
    // 落库列是 DECIMAL(10,2)：不卡上限的话超大值会在 JDBC 层溢出、裸奔成 500「未知异常」
    @Digits(integer = 8, fraction = 1, message = "框数最多 8 位整数 + 1 位小数")
    private BigDecimal boxCount;

    /**
     * 确认人 user_id（必填，前端默认填当前登录人，可改选养殖部人员）。
     */
    @NotNull(message = "确认人员不能为空")
    private Long confirmUserId;
}
