package org.dromara.djs.store.operation.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 门店销售流水实体（STR-OP-001）。
 *
 * <p>对应表 {@code t_store_sale_record}（V202606141210 重建）。V1 不做交易：数据来源仅
 * 手录（{@code source='manual'}）+ Excel 导入（{@code source='excel_import'}），
 * 无 POS / 微信支付对接，<b>不含</b>交易自动化字段（sale_no / unit_price / total_amount /
 * member_id / payment_method）。</p>
 *
 * <p>主键用雪花 ID，不暴露业务单号给用户（doc/11 §3.4 权威无 sale_no）。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_sale_record")
public class StoreSaleRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 门店 ID（FK → {@code t_md_store.id}）。
     */
    private Long storeId;

    /**
     * 产品 ID（FK → {@code t_warehouse_product_info.id}）。
     */
    private Long productId;

    /**
     * 冗余产品名（导入 / 快照）。
     */
    private String productName;

    /**
     * 销售日期（只到天）。
     */
    private Date saleDate;

    /**
     * 销售数量。
     */
    private BigDecimal saleQty;

    /**
     * 单位（冗余自 product）。
     */
    private String saleUnit;

    /**
     * 销售总额（元）。
     */
    private BigDecimal saleAmount;

    /**
     * 手录店员（FK → {@code sys_user.user_id}）。
     */
    private Long operatorId;

    /**
     * 数据来源（字典 {@code djs_sale_source}：manual=手录 / excel_import=Excel 导入）。
     */
    private String source;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
