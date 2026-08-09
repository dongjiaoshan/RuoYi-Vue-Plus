package org.dromara.djs.store.demand.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.store.demand.domain.bo.StoreDemandQuantityBo;
import org.dromara.djs.store.demand.domain.vo.StoreDemandCatalogVo;
import org.dromara.djs.store.demand.domain.vo.StoreDemandDayVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 门店小程序需求服务（V6 row62-70「门店小程序版块」专用查询 / 编辑）。
 *
 * <p>与 {@link IStoreDemandService} 分工：那边是「写」（创建即提交 / 购物车整单 / 收货确认，
 * admin + mp 共用）；本接口是 <b>mp 门店板块自己的读模型 + 行级改量删行</b>——
 * 按天聚合卡、某天明细、下单目录，都是小程序页面形态决定的查询，不进 admin 链路。</p>
 *
 * <p>薄封装铁律照旧：不复写状态机 / 编码 / 删除逻辑，删行直接 delegate
 * {@code IDemandManageService.deleteWithValidByIds}。</p>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
public interface IStoreDemandAppletService {

    /**
     * 按天聚合的需求卡列表（row66）。
     *
     * <p>一行 = 门店某一天的全部需求汇总；统计口径统一排除门店态 {@code DELETED} 的行，
     * 某天全删则该天整条不出现。分页在 SQL 层。</p>
     *
     * @param storeId   门店 ID（null 不过滤；mp 恒传当前门店）
     * @param beginDate 需求日期起（可空）
     * @param endDate   需求日期止（可空）
     * @param pageQuery 分页参数
     * @return 按天聚合分页（需求日期倒序）
     */
    TableDataInfo<StoreDemandDayVo> queryDayList(Long storeId, LocalDate beginDate, LocalDate endDate,
                                                 PageQuery pageQuery);

    /**
     * 某天的需求明细（row70 详情页）。
     *
     * <p>门店态多选筛选<b>下推到 SQL</b>（不查全量再内存筛），永不返回门店态 {@code DELETED} 的行，
     * 结果按 {@code id ASC} 稳定排序。VO 复用 {@link DemandManageVo}，额外回填
     * {@code storeDemandStatus / belongType / imageUrl}。</p>
     *
     * @param storeId       门店 ID（必填）
     * @param demandDate    需求日期（必填）
     * @param productName   产品名模糊（可空）
     * @param storeStatuses 门店视角态多选，逗号分隔（可空 = 不按状态筛）
     * @return 需求明细行
     */
    List<DemandManageVo> queryDayDetail(Long storeId, LocalDate demandDate, String productName, String storeStatuses);

    /**
     * 下单目录（row68）。
     *
     * <p>服务端负责过滤（启用 + 自产 + 非原材料，白条/礼盒豁免）、关键字模糊、排序、上限 1000 条，
     * 前端不再排序。</p>
     *
     * @param storeId 门店 ID（必填，{@code lastOrderTime} 是「本店」口径）
     * @param keyword 产品名 / 别名模糊（可空）
     * @return 目录行（已排好序）
     */
    List<StoreDemandCatalogVo> queryCatalog(Long storeId, String keyword);

    /**
     * 改需求量 / 删行（row70）。
     *
     * <p>{@code demandQuantity > 0} 改量；{@code = 0} 走既有删除路径。只允许门店态
     * {@code SUBMITTED}（待确认）的行；并校验该行门店属于当前用户可见门店集合
     * （关墙时恒放行，开墙后自动收紧）。</p>
     *
     * @param bo 需求 ID + 目标数量
     */
    void updateQuantity(StoreDemandQuantityBo bo);

    /**
     * 门店小程序整单下单（V6 row69）—— 在 admin 共用的 {@code batchCreate} 之上加三道 mp 侧闸。
     *
     * <p>为什么不直接调 {@code IStoreDemandService.batchCreate}：那条路径是 admin / mp 共用的，
     * 它信任调用方传进来的 {@code productType}、不校验需求日期下界、原材料黑名单也漏了 {@code pork}。
     * mp 是**面向店员的开放入口**，这三件事必须在服务端收口（客户端限制可被直连接口绕过）：</p>
     *
     * <ol>
     *   <li><b>需求日期不得早于今天</b>——甲方 row69「最早只能是当天」。前端 picker 的
     *       {@code min-date} 与提交前钳制都基于<b>设备本地时间</b>，手机时钟错了就跟着错，直连接口更是零阻力。</li>
     *   <li><b>{@code productType} 一律由服务端按产品 {@code belong_type} 推导</b>，不信客户端传值。
     *       这个字段决定需求单号的 bizCode 段与下游分拣发货的业态筛选：把果蔬声明成 {@code gift_box}
     *       就能拿到礼盒段单号、并在发货侧被当礼盒处理。</li>
     *   <li><b>原材料一律拒</b>（{@code product_attr=2}），白条 / 礼盒豁免 —— 与
     *       {@code queryCatalog} 的候选谓词<b>逐字相同</b>。共用那条路径的黑名单只覆盖
     *       egg/dry_good/other/vegetable，漏了 pork，导致「目录里根本不给选的猪肉原料，直连接口能下单」
     *       这种前后端谓词不一致。</li>
     * </ol>
     *
     * <p><b>本轮只收口 mp 这一侧</b>，admin 的既有行为一个字不动（它有自己的操作员与审核链路，
     * 收紧共用路径会波及本轮范围外的场景）。</p>
     *
     * @param bo 整单（门店 + 需求日期 + 明细行）
     * @return 落库条数
     */
    int batchCreate(StoreDemandBatchBo bo);

    /**
     * 门店小程序单条下单（V6，`/djs/applet/store/demand/add`）—— 与 {@link #batchCreate} <b>同三道闸</b>。
     *
     * <p>为什么单独有这一条：`/add` 与 `/batch` 是同一个 controller 上的两扇门。闸只装在 batch 上时，
     * 一条 `/add` 请求可以同时打穿三道（原材料 + 谎报业态拿到错单号段 + 落在昨天），
     * 独立验收已线上实证。已发布产物里 `pages/store/demand/form` 这个旧页调的正是 `/add`。</p>
     *
     * @param bo 单条需求（门店 + 产品 + 需求日期 + 数量）
     * @return 新建需求 ID
     */
    Long create(DemandManageBo bo);
}
