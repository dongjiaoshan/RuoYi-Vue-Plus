package org.dromara.djs.breed.farm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 农场主数据实体（BRD-MD-002）。
 *
 * <p>对应表 {@code sys_farm}（由 SYS-AUTH-001 V202605201100 建表 + seed
 * {@code id=1001, farm_code='DJS-MAIN', farm_name='东角山主场'}）。</p>
 *
 * <p>V1 单农场（{@link org.dromara.djs.common}）：admin 不暴露 list / add / remove，
 * 仅 {@code GET /current} 查 id=1001 一行 + {@code PUT /contact} 编辑联系信息三字段
 * （contact_name / contact_phone / address，其他字段 admin 不可见 / 不可改）。</p>
 *
 * <p>本实体<b>不继承 {@code TenantEntity}</b>：sys_farm 表本身即多租户主数据
 * （tenant_id = id 的语义来源），不再叠加 tenant_id 字段。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_farm")
public class FarmInfo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 农场 ID（V1 固定 1001）。
     */
    @TableId
    private Long id;

    /**
     * 农场编码（V1 固定 {@code DJS-MAIN}，admin 不可编辑）。
     */
    private String farmCode;

    /**
     * 农场名称（admin 不可编辑）。
     */
    private String farmName;

    /**
     * 农场状态（字典 {@code djs_farm_status}：{@code 0}=启用 / {@code 1}=停用，admin 不可编辑）。
     */
    private Integer farmStatus;

    /**
     * 联系人（admin 可编辑）。
     */
    private String contactName;

    /**
     * 联系电话（admin 可编辑）。
     */
    private String contactPhone;

    /**
     * 地址（admin 可编辑）。
     */
    private String address;

    /**
     * 备注。
     */
    private String remark;

}
