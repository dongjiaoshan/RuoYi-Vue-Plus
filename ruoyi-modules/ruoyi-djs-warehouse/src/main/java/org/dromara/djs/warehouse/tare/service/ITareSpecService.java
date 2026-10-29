package org.dromara.djs.warehouse.tare.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.tare.domain.bo.TareSpecBo;
import org.dromara.djs.warehouse.tare.domain.vo.TareOptionVo;
import org.dromara.djs.warehouse.tare.domain.vo.TareSpecVo;

import java.util.Collection;
import java.util.List;

/**
 * 去皮规格 Service。
 *
 * @author djs
 * @since V7-R2
 */
public interface ITareSpecService {

    TableDataInfo<TareSpecVo> queryPageList(String specName, PageQuery pageQuery);

    TareSpecVo queryById(Long id);

    int insertByBo(TareSpecBo bo);

    int updateByBo(TareSpecBo bo);

    int deleteByIds(Collection<Long> ids);

    /**
     * 某称重页可选的去皮规格：实际默认项排第一并标 {@code selected}，其余按更新时间倒序。
     *
     * @param position 字典 {@code djs_tare_position} 值
     */
    List<TareOptionVo> listOptions(String position);

}
