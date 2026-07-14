package org.dromara.djs.store.trace.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.trace.domain.bo.StoreTraceOnsiteBo;
import org.dromara.djs.store.trace.domain.vo.StoreOnsiteCodeVo;
import org.dromara.djs.store.trace.domain.vo.StorePackProductVo;
import org.dromara.djs.store.trace.domain.vo.TraceablePigVo;
import org.dromara.djs.warehouse.trace.domain.query.TraceCodeQuery;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeDetailVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeListVo;

import java.util.List;

/**
 * 门店现场生码服务（STORE-TRACE-ONSITE-001，门店域薄壳）。
 *
 * <p>编排两个跨域协作：<br>
 * ① 可追溯猪只 picker → 委托养殖域 {@code IPigQueryService.listTraceablePigs}（不直接读养殖表）；<br>
 * ② 现场按需生码 → 委托仓库域 {@code ITraceService.genPorkOnsiteCode}（trace 表归 warehouse，INSERT 在 warehouse）。<br>
 * 门店模块本身不持有 trace / pig 表 mapper，纯 orchestration 避免反向依赖。</p>
 *
 * <p><b>已生成猪肉追溯码管理（ADMIN-STORE 91-1）</b>：参照果蔬追溯 listTrace 范式，补已生码列表 / 详情 /
 * 补打取数三个只读端点，全部委托仓库域 {@code ITraceCodeAdminService}（追溯表归 warehouse，门店侧只读查询不反向写）。
 * 门店口径恒固定 {@code codeType='pork'}，前端 PorkTracePanel 只看猪肉追溯码。</p>
 *
 * @author djs
 * @since STORE-TRACE-ONSITE-001
 */
public interface IStoreTraceService {

    /**
     * 分页查可追溯猪只（已出栏育肥猪，供现场生码 picker）。
     *
     * @param pageQuery 分页参数
     * @return 分页 VO（耳号 / 性别 / 品种 / 日龄）
     */
    TableDataInfo<TraceablePigVo> listTraceablePigs(PageQuery pageQuery);

    /**
     * 门店猪肉打包可选产品列表（docx「门店猪肉打包」产品取数）。
     *
     * <p>两步取数：① 字典 {@code djs_pork_return_product} 的 value（产品业务码）resolve 出产品 id 集；
     * ② 查 {@code t_warehouse_product_info}，取 {@code product_workshop=5（门店打包间）} 且
     * {@code product_material ∈ 上述 id 集} 的猪肉产品。空 → 空 List（前端按部位字典兜底）。</p>
     *
     * @return 可打包产品列表（产品卡数据源）
     */
    List<StorePackProductVo> listPackProducts();

    /**
     * 门店现场按需生码（猪只 + 部位 + 重量 → 生成 pork 追溯码 + 门店生产编码）。
     *
     * @param bo 现场生码入参
     * @return 追溯码 produce_code（二维码用）+ 门店生产编码 {@code <生产标识码>YYMMDD####}（标签展示用）
     */
    StoreOnsiteCodeVo genOnsiteCode(StoreTraceOnsiteBo bo);

    /**
     * 已生成猪肉追溯码分页列表（ADMIN-STORE 91-1，恒 {@code codeType='pork'}）。
     *
     * <p>委托仓库 {@code ITraceCodeAdminService.queryPage}，入参强制覆写 {@code codeType=pork}
     * （门店端只看猪肉追溯码，前端传入的其它 codeType 一律忽略）。支持按追溯码 / 猪只耳号 /
     * 生成时间范围过滤。</p>
     *
     * @param query     查询条件（codeType 由 service 强制为 pork）
     * @param pageQuery 分页参数
     * @return 追溯码列表分页（含 JOIN 展示名）
     */
    TableDataInfo<TraceCodeListVo> listPorkTrace(TraceCodeQuery query, PageQuery pageQuery);

    /**
     * 已生成猪肉追溯码详情（主表 + 关联 + 完整事件时间轴）。委托仓库 {@code ITraceCodeAdminService.getDetail}。
     *
     * @param id 追溯码记录 ID
     * @return 详情聚合 VO
     */
    TraceCodeDetailVo getPorkTraceDetail(Long id);

    /**
     * 补打取数：按勾选 ID 批量拉详情（含事件链），供前端 jsPDF 批量补打追溯码。
     * 委托仓库 {@code ITraceCodeAdminService.batchDetail}。
     *
     * @param ids 追溯码记录 ID 列表
     * @return 详情聚合 VO 列表
     */
    List<TraceCodeDetailVo> batchPorkTraceDetail(List<Long> ids);
}
