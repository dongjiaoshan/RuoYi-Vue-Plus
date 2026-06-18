package org.dromara.djs.warehouse.veg.service;

import org.dromara.djs.warehouse.veg.domain.vo.FeedDailyStatVo;

import java.util.Date;
import java.util.List;

/**
 * 饲料饲喂台账 Service（WMS-VEG-FEED-LOG-001）。
 *
 * @author djs
 * @since WMS-VEG-FEED-LOG-001
 */
public interface IFeedLogService {

    /**
     * 按自然日 × 作物品类统计饲喂重量（spec 步8）。
     *
     * @param startDate 起始日期（含，可空）
     * @param endDate 截止日期（含，可空）
     * @return 每日每作物饲喂重量合计，按日期倒序
     */
    List<FeedDailyStatVo> dailyStat(Date startDate, Date endDate);

}
