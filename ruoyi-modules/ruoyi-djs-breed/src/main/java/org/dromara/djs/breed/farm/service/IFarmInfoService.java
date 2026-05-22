package org.dromara.djs.breed.farm.service;

import org.dromara.djs.breed.farm.domain.bo.FarmContactBo;
import org.dromara.djs.breed.farm.domain.vo.FarmInfoVo;

/**
 * 农场主数据 Service（BRD-MD-002）。
 *
 * <p>V1 单农场（{@code id=1001}），仅暴露：</p>
 * <ul>
 *   <li>{@link #queryCurrent()}：查当前农场只读详情</li>
 *   <li>{@link #updateContact(FarmContactBo)}：编辑联系信息三字段
 *       （contact_name / contact_phone / address）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MD-002
 */
public interface IFarmInfoService {

    /**
     * 查询当前农场详情（V1 hardcode {@code id=1001}）。
     *
     * @return 农场详情；若 sys_farm 表内 id=1001 不存在，返回 {@code null}（由 Controller 透传，
     *         前端弹错"农场数据未初始化"）
     */
    FarmInfoVo queryCurrent();

    /**
     * 修改联系信息（仅 contact_name / contact_phone / address 三字段）。
     *
     * @param bo 入参；{@code id} 必填且必须等于 1001
     * @return 受影响行数（成功 1）
     * @throws org.dromara.common.core.exception.ServiceException
     *         id 缺失 / id != 1001 / 目标行不存在
     */
    int updateContact(FarmContactBo bo);

}
