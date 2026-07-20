package org.dromara.djs.warehouse.flow.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 包材库「库存盘点」入参（mp 端 POST {@code /applet/warehouse/packing/check}，原型图54 per 项 4 动作之一）。
 *
 * <p>per 项轻量盘点（区别于 WMS-STOCK-001 admin 整库位盘点单 header/line 模型）：工人对单个包材
 * 录实盘量，service 同事务（参 {@code StockCheckServiceImpl#completeCheck} 单 line 逻辑）：</p>
 * <ol>
 *   <li>取系统现量 → 算差异 {@code diff = checkStock - sysStock}</li>
 *   <li>差异 != 0 → 写差异流水（盘盈 {@code check_in/IN} / 盘亏 {@code check_out/OT}，change 取差异）</li>
 *   <li>UPDATE location_stock 至实盘绝对值 + 刷新 {@code latest_check_time} + {@code check_result}；
 *       无库存行则按实盘量 INSERT 建账</li>
 * </ol>
 *
 * <p>盘点是「以实盘校准账面」直接 SET 绝对值，与领用/退回的增量语义不同。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-MATISSUE-001
 */
@Data
public class PackCheckBo {

    /**
     * 产品 ID（包材）。
     */
    @NotNull(message = "产品 ID 不能为空")
    private Long productId;

    /**
     * 库位 ID（可空，service 按 productId 解析默认库位；无库位行则首次盘点建账到默认库）。
     */
    private Long locationId;

    /**
     * 实盘量（必填，≥ 0；设为库存新绝对值）。
     */
    @NotNull(message = "实盘量不能为空")
    @DecimalMin(value = "0", message = "实盘量不能为负")
    private BigDecimal checkStock;

    /**
     * 盘点结果（可选，djs_check_result：1=正常 / 2=异常 / 3=计损；为空时按差异是否为 0 自动判定）。
     */
    private Integer checkResult;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "凭证图过多")
    private String proofOssIds;

    /**
     * 备注（可选，差异原因）。
     */
    @Size(max = 500, message = "备注最多 500 字")
    private String remark;

}
