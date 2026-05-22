package org.dromara.djs.common.store.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;

/**
 * 门店主数据实体（SYS-MD-002）。
 *
 * <p>对应表 {@code t_md_store}，门店域 {@code t_store_*} 业务表通过 {@code store_id} 关联本表。
 * 主要消费方：STR-OP-001 销售流水 / STR-MEMBER-001 会员档案 / STR-STOCK-001 盘点 / TRC-* 追溯。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}。服务层走
 * {@link DjsBaseServiceImpl#softDelete}（纯 wrapper update 写 {@code del_flag='1'} + {@code del_unique=id}），
 * 不要直接调 {@code deleteByIds}（参 {@link DjsBaseServiceImpl} 类注释）。UNIQUE(tenant_id, store_code, del_unique)
 * 保证软删后重启用同编码不冲突。</p>
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
     * 门店类型（V1 自由文本：{@code direct}=直营 / {@code franchise}=加盟；
     * V2 视客户实际需求决定是否上字典 {@code djs_store_type}）。
     */
    private String storeType;

    /**
     * 经营状态（{@code 1}=合作中 / {@code 0}=已终止）。
     *
     * <p>注：表字段为 TINYINT 1/0，UI 用 el-switch 渲染；不挂字典 {@code djs_store_status}
     * （后者 0/1/2 三态语义与本字段 1/0 二态不匹配，已 raise 到 D03 _open-issues 由 closing 决策）。</p>
     */
    private Integer businessStatus;

    /**
     * 门店地址。
     */
    private String address;

    /**
     * 联系人。
     */
    private String contactName;

    /**
     * 联系电话。
     */
    private String contactPhone;

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
