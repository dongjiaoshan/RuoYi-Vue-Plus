package org.dromara.djs.warehouse.flow.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 包材库「产品入库」入参（mp 端 POST {@code /applet/warehouse/packing/in}，原型图54 per 项 4 动作之一）。
 *
 * <p>Service 同事务（参 {@code MatFlowServiceImpl#pick} 范式）：</p>
 * <ol>
 *   <li>INSERT stock_flow（{@code flow_type='purchase_in'}, {@code inout_type='IN'}, {@code change_num=+quantity}）</li>
 *   <li>UPDATE location_stock 加库存；库存行不存在则 INSERT 新建账（包材首次入库）</li>
 * </ol>
 *
 * <p>跨层契约：{@code productId} 全链路 string（snowflake 19 位防截断）；{@code locationId} 可空
 * （包材库 per 项动作不让工人选库位，service 按 productId 解析默认库位，无库位行则首次建账到默认库）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-MATISSUE-001
 */
@Data
public class PackInBo {

    /**
     * 产品 ID（包材，belong_type='package'）。
     */
    @NotNull(message = "产品 ID 不能为空")
    private Long productId;

    /**
     * 库位 ID（可空）。
     *
     * <p>为空时 service 按 {@code productId} 解析默认库位（包材库 per 项动作工人不选库位）；
     * 产品首次入库（无任何 location_stock 行）→ service 兜底取该产品历史库位或抛异常引导先建库存。</p>
     */
    private Long locationId;

    /**
     * 入库数量（必填，&gt; 0）。
     */
    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.001", message = "入库数量必须大于 0")
    private BigDecimal quantity;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "凭证图过多")
    private String proofOssIds;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "备注最多 500 字")
    private String remark;

}
