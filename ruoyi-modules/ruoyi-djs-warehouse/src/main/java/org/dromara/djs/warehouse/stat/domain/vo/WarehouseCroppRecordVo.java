package org.dromara.djs.warehouse.stat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 作物日数据记录视图对象（WMS-STAT-001，邓博 admin row17）。
 *
 * <p>实体字段 + JOIN {@code t_plant_crop_info} 取的展示列（cropCode/cropName/imageOssId）。
 * 自定义 @Select 聚合（mapper {@code selectCroppPage}），雪花 crop_id CAST AS CHAR 防精度丢失。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Data
public class WarehouseCroppRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期。 */
    private LocalDate statDate;
    /** 作物 ID（String 防精度丢失）。 */
    private String cropId;
    /** 作物编码（JOIN crop_info）。 */
    private String cropCode;
    /** 作物名称（JOIN crop_info）。 */
    private String cropName;
    /** 作物图 oss_id（COALESCE(crop_image_preview, image_oss_id)，可空）。 */
    private String imageOssId;

    /** 采摘量。 */
    private BigDecimal pickWeight;
    /** 饲喂量。 */
    private BigDecimal feedWeight;
    /** 毛菜处理率%。 */
    private BigDecimal vegHandleRate;
    /** 接收量。 */
    private BigDecimal receiveWeight;
    /** 发往月台量。 */
    private BigDecimal sendPlatformWeight;
    /** 路损率%。 */
    private BigDecimal transportLossRate;
    /** 出库量。 */
    private BigDecimal outWeight;
    /** 净菜损耗率%。 */
    private BigDecimal netVegLossRate;
}
