package org.dromara.djs.breed.med.record.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.med.record.domain.MedRecord;
import org.dromara.djs.breed.med.record.domain.vo.MedRecordVo;

/**
 * 用药治疗流水 Mapper（BRD-MED-003）。
 *
 * <p>MP 通用 CRUD 即可。mp「使用药品」picker 的「近 3 天已领药品」数据源改由仓库
 * {@code MedicineStockProvider.listRecentPickedMedicineIds}（仓库领用出库流水 dept_pick_out，
 * 覆盖疫苗药品页 + 物资领用药品库两个领用入口，row131）提供，本 Mapper 不再自持该查询。</p>
 *
 * @author djs
 * @since BRD-MED-003
 */
public interface MedRecordMapper extends BaseMapperPlus<MedRecord, MedRecordVo> {

}
