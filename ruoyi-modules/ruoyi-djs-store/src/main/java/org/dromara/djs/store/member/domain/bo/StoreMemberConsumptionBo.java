package org.dromara.djs.store.member.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 会员手录消费 BO（STR-MEMBER-001）。
 *
 * <p>选会员 + 消费日期 + SKU 自由文本 + 数量 + 手填金额 + 备注。V1 不强校验金额、不强联 product_id。
 * 录入人靠 {@code create_by} 自动 fill，不收 operatorId。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Data
public class StoreMemberConsumptionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员 ID。
     */
    @NotNull(message = "会员不能为空")
    private Long memberId;

    /**
     * 消费日期（DATETIME）。
     */
    @NotNull(message = "消费日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date consumeDate;

    /**
     * 门店 ID（可空，默认取会员所属门店）。
     */
    private Long storeId;

    /**
     * 商品 SKU / 产品编码（自由文本）。
     */
    private String sku;

    /**
     * 数量。
     */
    private BigDecimal quantity;

    /**
     * 手填金额（元，不强校验）。
     */
    private BigDecimal amountManual;

    /**
     * 备注。
     */
    private String notes;

}
