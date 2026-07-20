package org.dromara.djs.warehouse.veg.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 饲料饲喂按自然日 × 作物品类统计 VO（WMS-VEG-FEED-LOG-001，spec 步8「每日统计饲喂作物品类及对应重量」）。
 *
 * <p>一行 = 某日某作物的饲喂重量合计（{@code SUM(feed_weight) GROUP BY feed_date, crop_id}）。</p>
 *
 * @author djs
 * @since WMS-VEG-FEED-LOG-001
 */
@Data
public class FeedDailyStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 饲喂日期（自然日）。序列化为 yyyy-MM-dd（DDL 列为 DATE，避免 Jackson 默认输出时间戳）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date feedDate;

    /**
     * 作物 ID（雪花，前端用 String 防精度丢失）。
     */
    private String cropId;

    /**
     * 作物名称。
     */
    private String cropName;

    /**
     * 当日该作物饲喂重量(kg) 合计。
     */
    private BigDecimal totalWeight;

}
