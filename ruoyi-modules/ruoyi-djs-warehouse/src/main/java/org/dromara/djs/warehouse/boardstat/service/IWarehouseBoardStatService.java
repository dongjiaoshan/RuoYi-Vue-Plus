package org.dromara.djs.warehouse.boardstat.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatDetailVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.InboundDetailRowVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.ProductionDetailRowVo;
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

    /**
     * 品类卡「入库明细」下钻：该品类当月计入入库量的流水逐条列出 + 按单位的全量合计。
     *
     * @param month      统计月份 yyyy-MM；空 = 当月
     * @param belongType 品类键 pork / vegetable / egg / dry_good（白名单外抛 400）
     * @param pageQuery  分页参数
     * @return 明细分页 + 合计（合计与品类卡同口径同数字）
     */
    BoardStatDetailVo<InboundDetailRowVo> getInboundDetail(String month, String belongType, PageQuery pageQuery);

    /**
     * 品类卡「生产明细」下钻：该品类当月计入生产量的生产记录逐条列出 + 按单位的全量合计。
     *
     * @param month      统计月份 yyyy-MM；空 = 当月
     * @param belongType 品类键 pork / vegetable / egg / dry_good（白名单外抛 400）
     * @param pageQuery  分页参数
     * @return 明细分页 + 合计（合计与品类卡同口径同数字）
     */
    BoardStatDetailVo<ProductionDetailRowVo> getProductionDetail(String month, String belongType, PageQuery pageQuery);
}
