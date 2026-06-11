package org.dromara.djs.common.image.service.impl;

import org.dromara.djs.common.image.api.ImageRematchProvider;
import org.dromara.djs.common.image.domain.ImageLibrary;
import org.dromara.djs.common.image.mapper.ImageLibraryMapper;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ImageLibraryServiceImpl#match} 匹配逻辑单测（IMG-LIB-001）。
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ImageLibraryServiceImpl.match 单元测试")
class ImageLibraryServiceImplTest {

    @Mock
    private ImageLibraryMapper imageLibraryMapper;

    @Mock
    private ImageUrlResolver imageUrlResolver;

    @Mock
    private ObjectProvider<ImageRematchProvider> rematchProviders;

    private ImageLibraryServiceImpl service;

    private ImageLibrary img(String name, String aliases, String ossId) {
        ImageLibrary i = new ImageLibrary();
        i.setImageName(name);
        i.setAliases(aliases);
        i.setOssId(ossId);
        i.setStatus("0");
        return i;
    }

    @BeforeEach
    void setUp() {
        when(imageLibraryMapper.selectList(any())).thenReturn(List.of(
            img("番茄", "西红柿,tomato", "2059550583492132866"),
            img("小白菜", null, "2059550583492132867"),
            img("无图作物", "noimg", null) // ossId 空 → 不进缓存
        ));
        service = new ImageLibraryServiceImpl(imageLibraryMapper, imageUrlResolver, rematchProviders);
        service.reload();
    }

    @Test
    @DisplayName("精确主名命中")
    void matchByExactName() {
        assertThat(service.match("小白菜")).isEqualTo("2059550583492132867");
    }

    @Test
    @DisplayName("别名 contains 命中")
    void matchByAlias() {
        assertThat(service.match("西红柿")).isEqualTo("2059550583492132866");
        assertThat(service.match("tomato")).isEqualTo("2059550583492132866");
    }

    @Test
    @DisplayName("匹配不上返 null（含 ossId 空的图库行不参与匹配）")
    void matchMiss() {
        assertThat(service.match("不存在的菜")).isNull();
        assertThat(service.match("noimg")).isNull(); // 别名命中但 ossId 空 → 未进缓存
        assertThat(service.match(null)).isNull();
        assertThat(service.match("  ")).isNull();
    }

    @Test
    @DisplayName("名称两侧空白被 trim 后仍命中")
    void matchTrimmed() {
        assertThat(service.match("  番茄  ")).isEqualTo("2059550583492132866");
    }

}
