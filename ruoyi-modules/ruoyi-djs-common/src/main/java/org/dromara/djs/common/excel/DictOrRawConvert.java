package org.dromara.djs.common.excel;

import cn.hutool.core.convert.Convert;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.excel.convert.ExcelDictConvert;

/**
 * 导出用字典转换器：字典能翻就落中文，翻不到回落原码（V6 row133）。
 *
 * <p>为什么不直接用 ruoyi 自带的 {@link ExcelDictConvert}：它 {@code getDictLabel} 查不到时
 * 返回**空串**，于是「字典类型写错」「字典里少一项」「数据里有历史脏码」这三种情况都会把整格导成空白——
 * 比现在导出裸码还糟（客户至少还能对着码猜，空白是彻底没了）。</p>
 *
 * <p>本类只在父类翻译结果为空时兜一层原值，其余行为完全一致。所以给字段挂错字典的最坏结果，
 * 是退回改造前的样子，不会丢数据。</p>
 *
 * @author djs
 * @since V6 row133
 */
public class DictOrRawConvert extends ExcelDictConvert {

    @Override
    public WriteCellData<String> convertToExcelData(Object object, ExcelContentProperty contentProperty,
                                                    GlobalConfiguration globalConfiguration) {
        WriteCellData<String> cell = super.convertToExcelData(object, contentProperty, globalConfiguration);
        if (cell != null && StringUtils.isNotBlank(cell.getStringValue())) {
            return cell;
        }
        // 原值本身为空（null / 空串）时保持空格子，不要把 "null" 写进去
        String raw = Convert.toStr(object, StringUtils.EMPTY);
        return new WriteCellData<>(raw);
    }

}
