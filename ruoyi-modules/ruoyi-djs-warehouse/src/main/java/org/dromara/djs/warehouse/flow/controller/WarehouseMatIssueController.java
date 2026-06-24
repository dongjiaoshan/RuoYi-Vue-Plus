package org.dromara.djs.warehouse.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.warehouse.flow.domain.bo.MatFeedBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueItemVo;
import org.dromara.djs.warehouse.flow.service.IMatFlowService;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生产物资领用 admin 端 Controller（WMS-MATPICK-ADMIN-001，行55 镜像小程序物资领用）。
 *
 * <p>与 mp {@code AppletMatFlowController}（{@code /applet/warehouse/mat}）共用同一套
 * {@link IMatFlowService} + 同一张 {@code t_warehouse_stock_flow} / {@code t_warehouse_location_stock}，
 * 数据完全互通。区别：</p>
 * <ul>
 *   <li>权限 {@code @SaCheckPermission("djs:warehouse:matPick:*")}（admin B 端角色，非 mp {@code @SaCheckLogin}）；</li>
 *   <li>列表行粒度（{@code location_stock} 行）+ 今日四量<b>按全部人</b>（admin 看全部记录，区别于 mp
 *       {@code myList} 按当前登录人）；</li>
 *   <li>操作（pick / return / loss / feed）操作人 = 当前登录 admin（service {@code LoginHelper.getUserId()} 兜底，
 *       BO 不显式传 operatorId），日期 = 当前时间。</li>
 * </ul>
 *
 * <p>列表 7 业态 tab（{@code package / white_bar / pork / vegetable / egg / dry_good / other}）按
 * {@code belong_type} 过滤；蔬菜（vegetable）业态前端额外开放「饲料饲喂」操作（行55）。</p>
 *
 * @author djs
 * @since WMS-MATPICK-ADMIN-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/matPick")
public class WarehouseMatIssueController extends BaseController {

    private final IMatFlowService matFlowService;
    private final LocationStockMapper locationStockMapper;
    private final ImageUrlResolver imageUrlResolver;

    /**
     * 行粒度待领产品列表（按业态 tab + 关键字过滤，今日四量按全部人）。
     *
     * <p>不分页（库存行有限，admin 一屏看全；前端 BizTable 本地分页）。productThumb 走 IMG-LIB-001
     * 批量 resolver 回填（L1 image_oss_id → L2 belong_type 默认图 → L3 全局），禁 N+1。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}（必填，tab 选中业态）
     * @param keyword    模糊关键字（可空；匹配产品名 / 库位名 / 耳号 / 地块编号）
     */
    @SaCheckPermission("djs:warehouse:matPick:list")
    @GetMapping("/list")
    public R<List<MatIssueItemVo>> list(@RequestParam String belongType,
                                        @RequestParam(required = false) String keyword) {
        return R.ok(loadRows(belongType, keyword));
    }

    /**
     * 领用出库（出库 → 扣库存 + 写 pick_out 流水；操作人 = 当前登录 admin）。
     */
    @SaCheckPermission("djs:warehouse:matPick:pick")
    @PostMapping("/pick")
    public R<Long> pick(@Valid @RequestBody MatPickBo bo) {
        bo.setOperatorId(null);
        // admin 生产物资领用镜像仓库打包领用（matPack）场景：后端据此回填 flow_type=prod_pick_out + dest=prod_pick
        bo.setSourceScene("warehouse");
        return R.ok(matFlowService.pick(bo));
    }

    /**
     * 退回入库（入库 → 加库存 + 写 return_in 流水 + 校验今日额度）。
     */
    @SaCheckPermission("djs:warehouse:matPick:return")
    @PostMapping("/return")
    public R<Long> returnBack(@Valid @RequestBody MatReturnBo bo) {
        return R.ok(matFlowService.returnBack(bo));
    }

    /**
     * 当日损耗（出库 → 扣库存 + 写 loss 流水 + 校验今日额度）。
     */
    @SaCheckPermission("djs:warehouse:matPick:loss")
    @PostMapping("/loss")
    public R<Long> loss(@Valid @RequestBody MatLossBo bo) {
        return R.ok(matFlowService.loss(bo));
    }

    /**
     * 饲料饲喂（行55 果蔬产品专属操作；出库 → 扣库存 + 写 feed_out 流水 + 写 feed_log）。
     */
    @SaCheckPermission("djs:warehouse:matPick:feed")
    @PostMapping("/feed")
    public R<Long> feed(@Valid @RequestBody MatFeedBo bo) {
        return R.ok(matFlowService.feed(bo));
    }

    /**
     * 导出当前业态行粒度列表（Excel）。
     */
    @SaCheckPermission("djs:warehouse:matPick:export")
    @PostMapping("/export")
    public void export(@RequestParam String belongType,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) {
        List<MatIssueItemVo> list = loadRows(belongType, keyword);
        ExcelUtil.exportExcel(list, "生产物资领用", MatIssueItemVo.class, response);
    }

    /**
     * 查询行粒度列表并批量回填 productThumb（list / export 共用）。
     */
    private List<MatIssueItemVo> loadRows(String belongType, String keyword) {
        List<String> belongTypes = List.of(belongType);
        String kw = StringUtils.isBlank(keyword) ? null : keyword.trim();
        List<MatIssueItemVo> items = locationStockMapper.selectAdminMatIssueRows(belongTypes, kw);
        if (items != null && !items.isEmpty()) {
            List<ImageUrlResolver.Item> resolveItems = items.stream()
                .map(v -> new ImageUrlResolver.Item(v.getProductThumb(), v.getBelongType()))
                .toList();
            List<String> urls = imageUrlResolver.resolveList(resolveItems);
            if (urls.size() == items.size()) {
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setProductThumb(urls.get(i));
                }
            }
        }
        return items;
    }

}
