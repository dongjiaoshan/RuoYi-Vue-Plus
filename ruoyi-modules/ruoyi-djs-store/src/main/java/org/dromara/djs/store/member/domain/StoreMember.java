package org.dromara.djs.store.member.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 门店会员档案实体（STR-MEMBER-001）。
 *
 * <p>对应表 {@code t_store_member}（D01 SYS-INIT-001 建表，本 ticket 不动 DDL）。
 * V1 会员只做「档案 + 手录消费」，不做 RFM / A-B-C 分类 / 营销分析（迁 V2）。</p>
 *
 * <p>会员是 C 端客户语义，但 V1 用本业务档案承载——不复用 {@code sys_user}、不建
 * {@code t_md_customer}（ADR-0007）。建档人 / 录入人靠 {@code create_by} 自动 fill。</p>
 *
 * <p>软删走 {@code delFlag} + {@code delUnique}
 * （{@link org.dromara.djs.common.base.DjsBaseServiceImpl#softDelete}）。
 * UNIQUE(tenant_id, member_no, del_unique) + UNIQUE(tenant_id, phone, del_unique)
 * 保证软删后重启用同编码 / 同手机号不冲突。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_member")
public class StoreMember extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员 ID（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 会员编号（{@link org.dromara.djs.common.encoder.BizCodeType#MEMBER_NO} 生成，格式 {@code 1{seq4}}，例 10001）。
     */
    private String memberNo;

    /**
     * 会员姓名。
     */
    private String memberName;

    /**
     * 手机号（UNIQUE(tenant_id, phone, del_unique)，service 层 add/edit 前查重）。
     */
    private String phone;

    /**
     * 会员等级（字典 {@code djs_member_level}：normal=普通 / vip=重要价值客户 / keep=重要保持客户）。
     */
    private String memberLevel;

    /**
     * 入会日期。
     */
    private Date joinDate;

    /**
     * 所属门店 ID（FK → {@code t_md_store.id}，可空）。
     */
    private Long storeId;

    /**
     * 会员标签（逗号分隔自由文本）。
     */
    private String memberTags;

    /**
     * 状态（1=正常 / 0=停用，DEFAULT 1）。
     */
    private Integer memberStatus;

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
     * 软删唯一性辅助列（软删时置为 id 值）。
     */
    private Long delUnique;

}
