package org.dromara.djs.common.image.service;

import org.dromara.djs.common.image.domain.DefaultImage;
import org.dromara.djs.common.image.mapper.DefaultImageMapper;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.mapper.SysOssMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ImageUrlResolver} 4 层兜底单测（IMG-LIB-001）。
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ImageUrlResolver 4 层兜底单测")
class ImageUrlResolverTest {

    @Mock
    private SysOssMapper sysOssMapper;

    @Mock
    private DefaultImageMapper defaultImageMapper;

    private ImageUrlResolver resolver;

    private SysOssVo oss(long id, String url) {
        SysOssVo vo = new SysOssVo();
        vo.setOssId(id);
        vo.setUrl(url);
        return vo;
    }

    private DefaultImage def(String key, String ossId) {
        DefaultImage d = new DefaultImage();
        d.setCategoryKey(key);
        d.setOssId(ossId);
        return d;
    }

    @BeforeEach
    void setUp() {
        // 默认图：vegetable=20002 / global=20003（white_bar 未配置 → 走 global）
        when(defaultImageMapper.selectList(any())).thenReturn(List.of(
            def("vegetable", "20002"),
            def("white_bar", null),
            def("global", "20003")
        ));
        // sys_oss url 表
        when(sysOssMapper.selectVoByIds(any())).thenReturn(List.of(
            oss(10001L, "http://oss/l1.jpg"),
            oss(20002L, "http://oss/veg-default.jpg"),
            oss(20003L, "http://oss/global-default.jpg")
        ));
        resolver = new ImageUrlResolver(sysOssMapper, defaultImageMapper);
    }

    @Test
    @DisplayName("L1：记录有 image_oss_id → 直接用它的 url")
    void resolveL1() {
        assertThat(resolver.resolve("10001", "vegetable")).isEqualTo("http://oss/l1.jpg");
    }

    @Test
    @DisplayName("L2：image_oss_id 空 → 按 belongType 兜底分类默认图")
    void resolveL2() {
        assertThat(resolver.resolve(null, "vegetable")).isEqualTo("http://oss/veg-default.jpg");
    }

    @Test
    @DisplayName("L3：image_oss_id 空 + 分类默认图未配置 → 全局兜底")
    void resolveL3() {
        // white_bar 默认图 oss_id=null → 退到 global
        assertThat(resolver.resolve(null, "white_bar")).isEqualTo("http://oss/global-default.jpg");
    }

    @Test
    @DisplayName("批量解析顺序一一对应")
    void resolveListInOrder() {
        List<String> urls = resolver.resolveList(List.of(
            new ImageUrlResolver.Item("10001", "vegetable"),
            new ImageUrlResolver.Item(null, "vegetable"),
            new ImageUrlResolver.Item(null, "white_bar")
        ));
        assertThat(urls).containsExactly(
            "http://oss/l1.jpg",
            "http://oss/veg-default.jpg",
            "http://oss/global-default.jpg");
    }

}
