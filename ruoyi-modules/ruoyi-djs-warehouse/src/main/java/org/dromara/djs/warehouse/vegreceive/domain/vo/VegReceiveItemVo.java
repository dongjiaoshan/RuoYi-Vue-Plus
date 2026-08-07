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
     * 产品 ID（row55；自产列表的<b>聚合键</b>，详情页与入库提交都按它收窄）。
     *
     * <p>自产：来自 {@code handle_record.product_id}（空则回落 {@code crop.related_product}）。
     * 一个作物可关联多个产品（红薯 / 红薯杆），各自一张卡、各自一份待入库量。
     * 外购：不填（外购的 {@code cropId} 本身就是产品 id）。</p>
     */
    private Long productId;

    /**
     * 产品名称（row55；自产卡片<b>显示用的名字</b>，取代原先显示作物名）。外购不填。
     */
    private String productName;

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
     * 产品类型文案（仅外购列表填，按 belong_type：果蔬/猪肉/蛋/白条/干货产品）。
     */
    private String productType;

    /**
     * 产品单位（{@code t_warehouse_product_info.product_unit}，如 kg/枚/份）。外购入库录入量按此单位
     * 展示（不一定是 kg，如鸡蛋=枚）；mp 外购入库 sheet 的数量单位用它。
     */
    private String productUnit;

    /**
     * 待入库重量(kg)。地块标记入库完成后，该地块的待入库量结算为损耗并清零（聚合不再正数挂着）。
     */
    private BigDecimal pendingWeight;

    /**
     * 实际入库量(kg)：仅自产列表填，= 该作物<b>详情页当前展示地块</b>的已入库量合计
     * （与详情页头卡同源，保证列表与详情一致）。
     */
    private BigDecimal actualWeight;

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
