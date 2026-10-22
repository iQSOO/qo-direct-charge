# 项目介绍视觉素材

README 的封面与官网入口使用 SVG 绘制，分别提供桌面、手机和深浅色版本。正文使用 GitHub 原生 Markdown 与 HTML 排版。

在仓库根目录运行：

```sh
python3 docs/presentation/generate.py
```

脚本只使用 Python 标准库，生成 `docs/assets/` 中的 `hero*.svg` 与 `website*.svg`。修改 `PALETTES` 可调整颜色，`hero()` 和 `website()` 控制文字及布局；同一输入始终生成相同内容。

README 通过 `<picture>` 选择适合当前宽度和配色偏好的图片，默认回退到浅色桌面版。SVG 不加载远程字体、脚本或第三方资源，正文链接与图片替代文本保留可访问性。

`ui-overview.png` 与 `ui-overview.svg` 是独立维护的界面布局展示，不由此脚本生成。
