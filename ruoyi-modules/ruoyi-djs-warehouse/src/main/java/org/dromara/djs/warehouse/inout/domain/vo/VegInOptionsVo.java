package org.dromara.djs.warehouse.inout.domain.vo;

import java.util.List;

/**
 * 果蔬入库工作台的固定选项（V6 row282）。
 *
 * @param destinationName 去向库位名称（毛菜保鲜库 L0006，采摘录入的唯一入库落点）
 * @param teams           采摘班组
 */
public record VegInOptionsVo(String destinationName, List<VegTeamOptionVo> teams) { }
