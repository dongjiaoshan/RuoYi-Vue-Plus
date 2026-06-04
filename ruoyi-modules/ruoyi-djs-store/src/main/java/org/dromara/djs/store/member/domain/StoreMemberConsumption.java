package org.dromara.djs.store.member.domain;

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
 * 门店会员手录消费记录实体（STR-MEMBER-001）。
 *
 * <p>对应表 {@code t_store_member_consumption}（D01 SYS-INIT-001 建表，本 ticket 不动 DDL）。
 * V1 手录消费：选会员 + 日期 + SKU 自由文本 + 数量 + 手填金额 + 备注，不强校验金额、不接 POS / 微信支付。</p>
 *
 * <p>录入人靠 {@code create_by}（ruoyi 自动 fill 当前登录 user_id，承载 operator 语义，ADR-0007）——
 * 本表 <b>无独立 operator_id 列</b>。</p>
 *
 * <p>⚠️ 本表 <b>无 {@code del_unique} 列、无 UNIQUE 约束</b>（与 {@code t_store_member} 不同）。
 * 软删只 set {@code del_flag='1'}，不 set {@code del_unique}
 * （{@link org.dromara.djs.store.member.service.impl.StoreMemberConsumptionServiceImpl#deleteByIds}
 * 自写不含 del_unique 的 wrapper update，<b>不复用基类 softDelete</b>）。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_member_consumption")
public class StoreMemberConsumption extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消费记录 ID（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 会员 ID（FK → {@code t_store_member.id}）。
     */
    private Long memberId;

    /**
     * 消费日期（实际 DDL 为 DATETIME）。
     */
    private Date consumeDate;

    /**
     * 门店 ID（可空）。
     */
    private Long storeId;

    /**
     * 商品 SKU / 产品编码（V1 自由文本）。
     */
    private String sku;

    /**
     * 产品 ID（可空，V1 留 NULL 不强联，保留待 V2）。
     */
    private Long productId;

    /**
     * 数量。
     */
    private BigDecimal quantity;

    /**
     * 手填金额（元，V1 不强校验金额）。
     */
    private BigDecimal amountManual;

    /**
     * 备注。
     */
    private String notes;

    /**
     * 备注（ruoyi BaseEntity remark；与业务 notes 区分，导出 / 列表用 notes）。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

}
