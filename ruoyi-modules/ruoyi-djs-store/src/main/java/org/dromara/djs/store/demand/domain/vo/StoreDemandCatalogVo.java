package org.dromara.djs.store.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店下单目录一行（mp 下单页右侧产品卡，V6 row68）。
 *
 * <p>服务端已完成过滤（启用 + 自产 + 非原材料，白条/礼盒豁免）、关键字模糊、排序，
 * 前端只按品类切片，<b>不再排序</b>。</p>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
@Data
public class StoreDemandCatalogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID（snowflake，Jackson 序列化为 string）。 */
    private Long productId;

    /**
     * 展示名（果蔬按原材料作物有机证书取产品名 / 别名，其余业态产品名）。
     * 与下单时定格到 {@code demand_manage.product_name} 的名字同源（{@code IProductDisplayNameResolver}）。
     */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 产品单位。 */
    private String productUnit;

    /** 产品品类（字典 {@code djs_belong_type}）。 */
    private String belongType;

    /** 产品图可访问 URL（后端已解析 ossId；无图为 null）。 */
    private String imageUrl;

    /**
     * 提交下单时落库的业态（仓库域 4 值 {@code white_bar/vegetable/gift_box/other}）。
     *
     * <p>后端按 {@code belongType} 算好直接下发，前端原样带回 —— 与 admin 购物车
     * {@code DemandCartDrawer.vue} 的 4 值映射逐字一致（猪肉/干货/鸡蛋/其他全部 → {@code other}）。</p>
     */
    private String demandProductType;

    /** 果蔬：关联作物最早采摘开始日 {@code yyyy-MM-dd}（无则 null）。非果蔬恒 null。 */
    private String earliestPickDate;

    /** 本店最近一次下单该产品的时间 {@code yyyy-MM-dd HH:mm}（从未下过为 null）。 */
    private String lastOrderTime;
}
