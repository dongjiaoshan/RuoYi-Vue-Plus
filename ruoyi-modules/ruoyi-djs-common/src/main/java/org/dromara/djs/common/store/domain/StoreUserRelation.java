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
 * 门店-人员关联实体（STORE-PERM-001 / STR-USER-REL-001）。
 *
 * <p>对应表 {@code t_store_user_relation}，多对多：一个门店可绑多个系统人员（{@code sys_user}），
 * 一人可绑多店。该关联<b>驱动</b>全局门店选择器的「我有权限门店」数据源
 * （{@code listStoreIdsByUser}）。与门店单店长 {@code t_md_store.manager_user_id} 是两套关系，互不替代。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（基类 {@link DjsBaseServiceImpl#softDelete}）。
 * UNIQUE(tenant_id, store_id, user_id, del_unique) 保证「解绑后重绑同一人」不冲突。</p>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_user_relation")
public class StoreUserRelation extends TenantEntity {

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
     * 人员 ID（FK → {@code sys_user.user_id}）。
     */
    private Long userId;

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
     * 软删唯一性辅助列：未删 0；软删时由 {@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
     * 写入 id，保证 UNIQUE(tenant_id, store_id, user_id, del_unique) 不阻塞重新绑定。
     */
    private Long delUnique;
}
