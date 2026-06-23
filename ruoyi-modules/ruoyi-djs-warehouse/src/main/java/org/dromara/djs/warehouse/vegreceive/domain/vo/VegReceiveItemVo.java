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
     * 产品业务编码（mp dock + inbound 页展示「产品编码」列；缺则前端显「-」）。
     *
     * <ul>
     *   <li>自产（{@code /self}）：优先取作物「关联产品」的业务码
     *       （{@code t_warehouse_product_info.product_id}，经 {@code crop.related_product} 解析）；
     *       作物未配关联产品时回退作物业务码 {@code t_plant_crop_info.crop_code}。</li>
     *   <li>外购（{@code /purchased}）：取产品业务码 {@code t_warehouse_product_info.product_id}。</li>
     * </ul>
     *
     * <p>这里是<b>业务码</b>（VARCHAR），不是 snowflake 主键，可直接展示。</p>
     */
    private String productCode;

    /**
     * 产品类型文案（仅外购列表填，如「果蔬产品」）。
     */
    private String productType;

    /**
     * 待入库重量(kg)。地块标记入库完成后，该地块的待入库量结算为损耗并清零（聚合不再正数挂着）。
     */
    private BigDecimal pendingWeight;

    /**
     * 损耗重量(kg)：仅自产列表填，= 该作物已标记入库完成行的损耗合计（row21）。
     */
    private BigDecimal lossWeight;

    /**
     * 作物 / 产品主图 ossId（= image_oss_id；IMG-LIB-001 L1，内部解析用，回填 thumbUrl 后前端不直接读）。
     */
    private String imageOssId;

    /**
     * 缩略图 URL（IMG-LIB-001 4 层 resolver 回填，自产 L2 兜底蔬菜默认图；前端 image 直接绑定，保证非空兜底）。
     */
    private String thumbUrl;

}
