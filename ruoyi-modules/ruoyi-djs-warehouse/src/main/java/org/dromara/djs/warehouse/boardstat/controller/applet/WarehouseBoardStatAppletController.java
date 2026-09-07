package org.dromara.djs.warehouse.boardstat.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatDetailVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.InboundDetailRowVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.ProductionDetailRowVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.WarehouseBoardStatVo;
import org.dromara.djs.warehouse.boardstat.service.IWarehouseBoardStatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序仓库管理「仓库统计」tab Controller（V6-R178）。
 *
 * <p>看板为只读聚合、无写操作，与兄弟端点
 * {@code /djs/applet/warehouse/dashboard/*} 一致仅挂 {@code @SaCheckLogin}：
 * 进得了「仓库管理」tab（tab 可见性由 {@code djs:mptab:warehouse:dashboard} gate）就看得到本 tab 的数。</p>
 *
 * <p>两个明细端点是品类卡上「入库明细 / 生产明细」两个链接的下钻页，
 * 权限与 {@code /category} 一致（同一张卡上的数，看得到卡就看得到卡里的行）。</p>
 *
 * @author djs
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/warehouse/boardstat")
public class WarehouseBoardStatAppletController {

    private final IWarehouseBoardStatService boardStatService;

    /**
     * 月度品类统计：4 张品类卡 × 若干单位 × 入库量 / 生产量 / 原材料消耗量 + 环比。
     *
     * @param month 统计月份 yyyy-MM（可空，缺省当月）
     * @return 月度品类统计 VO
     */
    @SaCheckLogin
    @GetMapping("/category")
    public R<WarehouseBoardStatVo> category(
        @RequestParam(value = "month", required = false) String month) {
        return R.ok(boardStatService.getCategoryStat(month));
    }

    /**
     * 「入库明细」下钻：该品类当月计入入库量的流水逐条列出，并带按单位的全量合计。
     *
     * @param month      统计月份 yyyy-MM（可空，缺省当月；格式非法 400）
     * @param belongType 品类键 pork / vegetable / egg / dry_good（白名单外 400）
     * @param pageQuery  分页参数（pageNum / pageSize）
     * @return 明细分页 + 合计
     */
    @SaCheckLogin
    @GetMapping("/inbound-detail")
    public R<BoardStatDetailVo<InboundDetailRowVo>> inboundDetail(
        @RequestParam(value = "month", required = false) String month,
        @RequestParam("belongType") String belongType,
        PageQuery pageQuery) {
        return R.ok(boardStatService.getInboundDetail(month, belongType, pageQuery));
    }

    /**
     * 「生产明细」下钻：该品类当月计入生产量的生产记录逐条列出，并带按单位的全量合计。
     *
     * @param month      统计月份 yyyy-MM（可空，缺省当月；格式非法 400）
     * @param belongType 品类键 pork / vegetable / egg / dry_good（白名单外 400）
     * @param pageQuery  分页参数（pageNum / pageSize）
     * @return 明细分页 + 合计
     */
    @SaCheckLogin
    @GetMapping("/production-detail")
    public R<BoardStatDetailVo<ProductionDetailRowVo>> productionDetail(
        @RequestParam(value = "month", required = false) String month,
        @RequestParam("belongType") String belongType,
        PageQuery pageQuery) {
        return R.ok(boardStatService.getProductionDetail(month, belongType, pageQuery));
    }
}
