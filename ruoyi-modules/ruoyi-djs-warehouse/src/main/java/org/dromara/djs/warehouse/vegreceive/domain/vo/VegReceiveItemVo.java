package org.dromara.djs.warehouse.vegreceive.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 果蔬待收货列表项 VO（FIX-WMS-VEGRECEIVE-001，自产 / 外购通用）。
 *
 * <p>对齐 mp 契约 {@code miniapp/src/api/warehouse/vegReceive.ts#VegReceiveItem}：
 * {@code cropId / cropName / productType? / pendingWeight}。</p>
 *
 * <ul>
 *   <li>自产（{@code /self}）：{@code cropId}=作物 ID，{@code pendingWeight}=该作物月台待入库总量
 *       （= {@code SUM(send_platform_weight)} − 已入库 self 量）；{@code productType} 不填</li>
 *   <li>外购（{@code /purchased}）：{@code cropId}=外购产品 ID，{@code pendingWeight}=参考量（V1 外购无预设待收量，置 0），
 *       {@code productType}=产品类型文案（如「果蔬产品」）</li>
 * </ul>
 *
 * <p>跨层契约：{@code cropId} 为 Long，Jackson 序列化为 String（ruoyi JacksonConfig 全局 Long→String）。</p>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
@Data
public class VegReceiveItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作物 ID（自产）/ 外购产品 ID（外购）。
     */
    private Long cropId;

    /**
     * 作物 / 产品名称。
     */
    private String cropName;

    /**
     * 产品类型文案（仅外购列表填，如「果蔬产品」）。
     */
    private String productType;

    /**
     * 待入库重量(kg)。
     */
    private BigDecimal pendingWeight;

}
