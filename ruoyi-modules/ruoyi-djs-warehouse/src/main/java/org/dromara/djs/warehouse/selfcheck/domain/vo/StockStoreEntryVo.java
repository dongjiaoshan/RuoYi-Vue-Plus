package org.dromara.djs.warehouse.selfcheck.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 各库入口卡 VO（mp 库存盘点首页：缩略图 + 库名 + 产品品种数 + 最近盘点摘要）。
 *
 * <p>对应 mp 契约 {@code stockCheck.ts} StockStoreEntryVo。按库位聚合：每库 distinct 产品维度计数
 * + MAX(latest_check_time) + 最近 check_result（service 转中文文案）。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
@Data
public class StockStoreEntryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位 ID（snowflake string，框架统一序列化为 string 防截断）。
     */
    private Long locationId;

    /**
     * 库名（如 包材库 / 白条库 / 蔬菜保鲜室）。
     */
    private String locationName;

    /**
     * 库类型字典 {@code djs_location_type}（frozen / fresh / dry / medicine ...）。
     */
    private String locationType;

    /**
     * 产品品种数（该库位 distinct 产品维度计数）。
     */
    private Integer productKinds;

    /**
     * 最近盘点日期 yyyy-MM-dd。
     */
    private String lastCheckDate;

    /**
     * 最近盘点结果文案（正常 / 异常 / 计损）。
     */
    private String lastCheckResult;

    /**
     * 缩略图 url（无统一转换则 null）。
     */
    private String thumbUrl;

}
