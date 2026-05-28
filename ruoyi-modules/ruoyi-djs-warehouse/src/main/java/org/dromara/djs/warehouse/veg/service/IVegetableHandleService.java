package org.dromara.djs.warehouse.veg.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.veg.domain.bo.HandleRecordSubmitBo;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleQuery;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegetableHandleVo;

import java.util.List;

/**
 * 毛菜处理 Service（WMS-VEG-001）。
 *
 * @author djs
 * @since WMS-VEG-001
 */
public interface IVegetableHandleService {

    /**
     * mp 提交处理记录（事务一致）。
     *
     * @return vegetable_handle.id（汇总主键，前端进入"我的"列表时回展）
     */
    Long submitHandleRecord(HandleRecordSubmitBo bo);

    /**
     * mp 待处理 planting_record 列表（最近 50 条 handle_status != 'done'）。
     */
    List<PendingPlantingRecordVo> listPending();

    /**
     * admin 列表（分页）。
     */
    TableDataInfo<VegetableHandleVo> queryPageList(VegHandleQuery query, PageQuery pageQuery);

    /**
     * admin 列表（导出，不分页）。
     */
    List<VegetableHandleVo> queryList(VegHandleQuery query);

    /**
     * admin 详情。
     */
    VegetableHandleVo queryById(Long id);

    /**
     * 详情下钻 handle_record 列表（admin / mp 通用）。
     */
    List<HandleRecordVo> listRecords(Long handleId);

    /**
     * mp "我的处理记录"（按 handle_user = current）。
     */
    TableDataInfo<HandleRecordVo> myRecords(PageQuery pageQuery);

}
