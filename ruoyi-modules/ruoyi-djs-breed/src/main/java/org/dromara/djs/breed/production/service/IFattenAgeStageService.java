package org.dromara.djs.breed.production.service;

import org.dromara.djs.breed.production.domain.bo.FattenAgeStageBo;
import org.dromara.djs.breed.production.domain.vo.FattenAgeStageVo;

import java.util.List;

/**
 * 育肥日龄阶段配置 Service（A1 Tab2）。
 *
 * <p>表单语义为「整张表格批量保存」：{@link #batchSave} 走「软删旧行 + 重灌新行」事务。</p>
 *
 * @author djs
 * @since A1
 */
public interface IFattenAgeStageService {

    /**
     * 查询全部日龄阶段（按起始日龄升序）。
     *
     * @return 阶段列表（未删的）
     */
    List<FattenAgeStageVo> queryList();

    /**
     * 批量保存：软删全部旧行后重灌入参 {@code stages}。
     *
     * <p>整表覆盖语义（admin 一次提交全部行），避免逐行 diff。空入参 = 清空全部。</p>
     *
     * @param stages 前端提交的全部阶段行
     * @return 新灌入的行数
     */
    int batchSave(List<FattenAgeStageBo> stages);

    /**
     * 软删单行。
     *
     * @param id 主键
     * @return 受影响行数
     */
    int removeById(Long id);

}
