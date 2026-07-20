package org.dromara.djs.warehouse.outsource.domain;

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
 * 外购猪只实体（DJS-FIX-WMS-RALN，原型「产品生产管理/外购猪只」）。
 *
 * <p>对应表 {@code t_warehouse_outsource_pig}。外购整头活猪登记台账：记录购买日期、到场时间、
 * 猪只标识号、毛猪重量、送宰日期、供应商、购买人。原型仅 新增 + 删除（无编辑）。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（{@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
 * 在 delFlag='1' 时把 id 写入 delUnique）。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_outsource_pig")
public class OutsourcePig extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 购买日期（必填）。
     */
    private Date purchaseDate;

    /**
     * 到场时间（datetime，可空）。
     */
    private Date arriveTime;

    /**
     * 猪只标识号（可空）。
     */
    private String pigMarkNo;

    /**
     * 毛猪重量 kg（必填）。
     */
    private BigDecimal pigWeight;

    /**
     * 送宰日期（必填）。
     */
    private Date slaughterDate;

    /**
     * 供应商 ID（{@code t_djs_supplier.id}，必填）。
     */
    private Long supplierId;

    /**
     * 镜像白条业务码（= {@code t_warehouse_bar_info.bar_id}）。录入时由 createOutsourceBar 回写，
     * 删除前据此反查白条状态做下游拦截（白条已燎毛/分割/入库等下游态则禁止删除外购台账）。
     */
    private String barId;

    /**
     * 购买人（{@code sys_user.user_id}，可空，VO 走 @Translation 翻译成姓名）。
     */
    private String buyer;

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
     * 写入 id。
     */
    private Long delUnique;

}
