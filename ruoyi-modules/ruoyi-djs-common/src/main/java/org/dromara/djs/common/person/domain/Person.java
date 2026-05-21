package org.dromara.djs.common.person.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 人员主数据实体（SYS-MD-001）。
 *
 * <p>对应表 {@code t_md_person}，跨业务域复用：</p>
 * <ul>
 *   <li>养殖：饲养员 / 兽医（关联 {@code t_farm_event_*.operator_id}）</li>
 *   <li>种植：种植工 / 收割工</li>
 *   <li>仓库 / 门店：仓管员 / 销售员</li>
 * </ul>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（{@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
 * 在 delFlag='1' 时把 id 写入 delUnique，保证 UNIQUE(tenant_id, person_code, del_unique) 不阻塞重新启用同编码）。</p>
 *
 * @author djs
 * @since SYS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_md_person")
public class Person extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人员 ID（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 人员编码（SYS-INFRA-004 编码生成器产出；当前 ticket 暂用 P + 时间戳占位）。
     */
    private String personCode;

    /**
     * 姓名。
     */
    private String name;

    /**
     * 性别（字典 sys_user_sex：0 男 / 1 女 / 2 未知）。
     */
    private String gender;

    /**
     * 联系电话。
     */
    private String phone;

    /**
     * 身份证号。
     */
    private String idCard;

    /**
     * 岗位（自由文本，便于业务侧描述；与 sys_post 关联走 {@link #postId}）。
     */
    private String position;

    /**
     * sys_post 外键（区分 admin 角色 vs 微信端角色）。
     */
    private Long postId;

    /**
     * 入职日期。
     */
    private LocalDate hireDate;

    /**
     * 状态（字典 djs_user_status：0 在职 / 1 离职 / 2 试用，由 D1 SYS-INIT-002 seed）。
     */
    private String status;

    /**
     * 头像 URL（SYS-INFRA-002 OSS 直传产物，本 ticket 暂不接入上传 UI）。
     */
    private String avatarUrl;

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
     * 写入 id，保证 UNIQUE 约束不阻塞重新启用同编码。
     */
    private Long delUnique;

}
