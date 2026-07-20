package org.dromara.djs.common.store.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 门店主数据实体（SYS-MD-002 + SYS-MD-FIX-002）。
 *
 * <p>对应表 {@code t_md_store}，门店域 {@code t_store_*} 业务表通过 {@code store_id} 关联本表。
 * 主要消费方：STR-OP-001 销售流水 / STR-MEMBER-001 会员档案 / STR-STOCK-001 盘点 / TRC-* 追溯。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}。服务层走
 * {@link DjsBaseServiceImpl#softDelete}（纯 wrapper update 写 {@code del_flag='1'} + {@code del_unique=id}），
 * 不要直接调 {@code deleteByIds}。UNIQUE(tenant_id, store_code, del_unique) 保证软删后重启用同编码不冲突。</p>
 *
 * @author djs
 * @since SYS-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_md_store")
public class Store extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店 ID（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 门店编码（{@link org.dromara.djs.common.encoder.BizCodeType#STORE_CODE} 生成，pattern {@code ST{seq4}}，例 {@code ST0001}）。
     */
    private String storeCode;

    /**
     * 门店名称。
     */
    private String storeName;

    /**
     * 门店简称。
     */
    private String shortName;

    /**
     * 开业日期。
     */
    private LocalDate openDate;

    /**
     * 门店类型（字典 djs_store_type：direct=直营 / franchise=加盟）。
     */
    private String storeType;

    /**
     * 合作状态（字典 djs_store_status：0=合作中 / 1=已终止 / 2=装修中）。
     */
    private String businessStatus;

    /**
     * 门店地址。
     */
    private String address;

    /**
     * 店长姓名（文本，编辑表单可改；与 manager_user_id 解耦）。
     */
    private String managerName;

    /**
     * 店长电话（文本）。
     */
    private String managerPhone;

    /**
     * 店长 sys_user.user_id（NULL=未设置；不允许通过编辑端点修改，必须走 PUT /djs/common/store/{id}/manager）。
     */
    private Long managerUserId;

    /**
     * 收银系统 ID。
     */
    private String posSystemId;

    /**
     * 生产标识码（门店级唯一，用于门店打包生产编码前缀，规则 {@code <生产标识码>YYMMDD####}）。
     * UNIQUE(tenant_id, production_mark_code, del_unique)，不同门店不可重复。
     */
    private String productionMarkCode;

    /**
     * 门店图片（引用 sys_oss.oss_id，不直接存 URL）。
     */
    private Long imageOssId;

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
     * 软删唯一性辅助列：未删时 0；软删时由 {@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
     * 写入 id，保证 UNIQUE(tenant_id, store_code, del_unique) 不阻塞重新启用同编码。
     */
    private Long delUnique;

}
