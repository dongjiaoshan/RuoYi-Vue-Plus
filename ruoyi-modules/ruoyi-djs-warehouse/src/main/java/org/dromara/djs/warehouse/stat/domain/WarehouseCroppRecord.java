package org.dromara.djs.warehouse.stat.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 作物日数据记录实体（WMS-STAT-001，表 {@code t_warehouse_cropp_record}，邓博 admin row17）。
 *
 * <p>每个 tenant_id + stat_date + crop_id 一行（一作物一行）；由
 * {@link org.dromara.djs.warehouse.stat.job.WarehouseStatJob} 每晚跑批落盘 T-1 作物维度果蔬指标。
 * 当日有任意处理记录的作物才落盘（不区分地块，只按作物聚合，A#9）。
 * admin「作物日报表」只读列表读本表，展示走 JOIN t_plant_crop_info 取 crop_code/crop_name/image。</p>
 *
 * <p>率类列分母≤0 落 NULL + {@code @TableField(updateStrategy = ALWAYS)}（同日表口径）。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_cropp_record")
public class WarehouseCroppRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 统计日期（T-1）。 */
    private LocalDate statDate;
    /** 作物 ID（FK → t_plant_crop_info.id）。 */
    private Long cropId;

    /** 采摘量（当日该作物毛菜处理间称重总重）。 */
    private BigDecimal pickWeight;
    /** 饲喂量（当日该作物饲料饲喂总重）。 */
    private BigDecimal feedWeight;
    /** 毛菜处理率%（(采摘总量−毛菜损耗量)/采摘总量×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal vegHandleRate;
    /** 接收量（当日该作物果蔬月台接收总重）。 */
    private BigDecimal receiveWeight;
    /** 发往月台量（当日该作物从毛菜处理间发往月台重）。 */
    private BigDecimal sendPlatformWeight;
    /** 路损率%（(发往月台量−接收量)/接收量×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal transportLossRate;
    /** 出库量（当日该作物生产领用出库量−生产退回量）。 */
    private BigDecimal outWeight;
    /** 净菜损耗率%（(生产损耗+录入损耗)/出库量×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netVegLossRate;

    /** 软删标志（业务表必含）。 */
    @TableLogic
    private String delFlag;
    /** 软删唯一标识。 */
    private Long delUnique;
}
