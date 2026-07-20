package org.dromara.djs.breed.core.service;

import org.dromara.djs.breed.core.domain.vo.InventoryBarnMatrixVo;
import org.dromara.djs.breed.core.domain.vo.InventoryBoarItemVo;
import org.dromara.djs.breed.core.domain.vo.InventoryDistItemVo;

import java.util.List;

/**
 * 库存看板聚合 - 小程序端（read-only，纯 query 聚合，BRD-INVENTORY-001）。
 *
 * <p>pigType 入参语义（5 段 type segment）：</p>
 * <ul>
 *   <li>sow        母猪（按 pig_type='sow' 过滤）</li>
 *   <li>fattening  肥猪（按 pig_type='fattening' 过滤）</li>
 *   <li>piglet     仔猪（按 pig_type='piglet' 过滤）</li>
 *   <li>reserve    后备猪（特殊：按 current_status='HB' 过滤，<b>不是</b> pig_type，
 *                  因 djs_pig_type 字典无后备项）</li>
 *   <li>boar       公猪（按 pig_type='boar' 过滤）</li>
 * </ul>
 */
public interface IInventoryAppletService {

    /**
     * 栋舍 × 状态矩阵。
     * 母猪段 byStatus 维度 = current_status；其它段无细分状态时退化为空 byStatus（mp 仅展示头数）。
     */
    List<InventoryBarnMatrixVo> barnMatrix(String pigType);

    /**
     * 胎次分布（仅母猪有意义；其它 type 返回空列表，mp 退化"暂无数据"）。
     */
    List<InventoryDistItemVo> parityDist(String pigType);

    /**
     * 日龄分布（按出生日期推算日龄分桶；缺出生日期的猪不计入桶）。
     */
    List<InventoryDistItemVo> ageDist(String pigType);

    /**
     * 公猪列表（耳号 / 品种 / 栋舍 / 引种日期）。
     */
    List<InventoryBoarItemVo> boarList();
}
