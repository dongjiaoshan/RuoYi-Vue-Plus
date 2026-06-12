package org.dromara.djs.store.trace.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.trace.domain.bo.StoreTraceOnsiteBo;
import org.dromara.djs.store.trace.domain.vo.TraceablePigVo;

/**
 * 门店现场生码服务（STORE-TRACE-ONSITE-001，门店域薄壳）。
 *
 * <p>编排两个跨域协作：<br>
 * ① 可追溯猪只 picker → 委托养殖域 {@code IPigQueryService.listTraceablePigs}（不直接读养殖表）；<br>
 * ② 现场按需生码 → 委托仓库域 {@code ITraceService.genPorkOnsiteCode}（trace 表归 warehouse，INSERT 在 warehouse）。<br>
 * 门店模块本身不持有 trace / pig 表 mapper，纯 orchestration 避免反向依赖。</p>
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
     * 门店现场按需生码（猪只 + 部位 + 重量 → 生成 pork 追溯码）。
     *
     * @param bo 现场生码入参
     * @return 生成的追溯码 produce_code
     */
    String genOnsiteCode(StoreTraceOnsiteBo bo);
}
