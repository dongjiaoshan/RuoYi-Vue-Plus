package org.dromara.djs.warehouse.boardstat.service;

import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatDetailVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.WarehouseBoardStatVo;

/**
 * mp 仓库管理「仓库统计」tab 服务（V6-R178 / R193）。
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

    /**
     * 品类卡「入库明细」下钻：统计月内该品类<b>每个入库产品</b>的合计量与环比。
     *
     * @param month      统计月份 yyyy-MM；空 = 当月，格式非法抛 400
     * @param belongType 品类键 pork / vegetable / egg / dry_good（白名单外抛 400）
     * @return 产品明细 + 按单位的合计（合计与品类卡同口径同数字）
     */
    BoardStatDetailVo getInboundDetail(String month, String belongType);

    /**
     * 品类卡「生产明细」下钻：统计月内该品类<b>每个生产产品</b>的合计量与环比。
     *
     * @param month      统计月份 yyyy-MM；空 = 当月，格式非法抛 400
     * @param belongType 品类键 pork / vegetable / egg / dry_good（白名单外抛 400）
     * @return 产品明细 + 按单位的合计（合计与品类卡同口径同数字）
     */
    BoardStatDetailVo getProductionDetail(String month, String belongType);
}
