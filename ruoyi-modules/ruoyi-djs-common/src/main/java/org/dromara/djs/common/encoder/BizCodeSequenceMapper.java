package org.dromara.djs.common.encoder;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * {@link BizCodeSequence} 持久层（SYS-INFRA-004）。
 *
 * <p>核心方法用原生 SQL 实现两件事：</p>
 * <ol>
 *   <li>{@link #incrementSeq(String, String, long)} — 原子 UPDATE 步长 N，返回影响行数（用于命中判断）</li>
 *   <li>{@link #selectCurrentSeq(String, String)} — 读取当前序号，配合 UPDATE 拿到新值</li>
 * </ol>
 *
 * <p>UPDATE 用 {@code current_seq = current_seq + #{step}} 的形式由 MySQL 保证行级原子性；
 * 上层 Redisson 分布式锁主要解决"行不存在时的并发插入"问题（INSERT IGNORE + 二次 UPDATE）。</p>
 *
 * @author djs
 * @since SYS-INFRA-004
 */
@Mapper
public interface BizCodeSequenceMapper extends BaseMapperPlus<BizCodeSequence, BizCodeSequence> {

    /**
     * 原子递增指定 {@code (codeType, seqDate)} 的当前序号（步长 step），返回受影响行数。
     *
     * <p>tenant_id 由 MyBatis-Plus 多租户拦截器自动注入到 WHERE，调用方无需传。</p>
     *
     * @param codeType 编码类型
     * @param seqDate  序号周期（yyyyMMdd / yyyyMM / yyyy / 空串）
     * @param step     步长（单条 = 1，批量 = count）
     * @return 影响行数（0 = 行不存在，需先 INSERT 初始化）
     */
    @Update("""
        UPDATE t_md_biz_code_sequence
        SET current_seq = current_seq + #{step},
            update_time = NOW()
        WHERE code_type = #{codeType}
          AND seq_date  = #{seqDate}
          AND del_flag  = '0'
        """)
    int incrementSeq(@Param("codeType") String codeType,
                     @Param("seqDate") String seqDate,
                     @Param("step") long step);

    /**
     * 读取当前序号（在 {@link #incrementSeq} 之后调，得到的就是 INCRBY 后的新值）。
     *
     * @return 当前序号；行不存在返 null
     */
    @Select("""
        SELECT current_seq
        FROM t_md_biz_code_sequence
        WHERE code_type = #{codeType}
          AND seq_date  = #{seqDate}
          AND del_flag  = '0'
        LIMIT 1
        """)
    Long selectCurrentSeq(@Param("codeType") String codeType,
                          @Param("seqDate") String seqDate);
}
