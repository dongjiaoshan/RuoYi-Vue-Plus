package org.dromara.djs.warehouse.boardstat.service;

import org.dromara.djs.warehouse.boardstat.domain.vo.WarehouseBoardStatVo;

/**
 * mp 仓库管理「仓库统计」tab 服务（V6-R178）。
 *
 * @author djs
 */
public interface IWarehouseBoardStatService {

    /**
     * 按月取 4 个品类的入库量 / 生产量 / 原材料消耗量及各自环比。
     *
     * @param month 统计月份 yyyy-MM；空 / 非法格式回落当月
     * @return 月度品类统计 VO（品类恒 4 张卡）
     */
    WarehouseBoardStatVo getCategoryStat(String month);
}
