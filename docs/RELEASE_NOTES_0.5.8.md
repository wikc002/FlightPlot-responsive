# FlightPlot 0.5.8 发布说明

发布日期：2026-09-13

0.5.8 是当前唯一保留和维护的版本。它将多轮迭代形成的日志打开、页状态、字段恢复和窗口生命周期收拢为统一流程，并针对大 DataFlash BIN 与 Windows 高 DPI 使用进行了完整回归。

## 用户可见改进

- 大 BIN 打开和 seek 更快，绘图时保留尖峰且减少内存占用。
- 窗口缩放、多屏和高 DPI 环境下，图表、字体、文件选择器与对话框更清晰。
- BIN、文本 LOG 和 ULog 按文件 schema 动态展示字段，可识别新固件字段。
- 连续拖入、重复打开和 20 个日志页操作保持响应，过期任务不再覆盖新页面。
- 相同格式日志自动恢复处理器、勾选状态和字段颜色。
- RCIN/RCOU 通道以及其他数字字段按自然顺序排列。
- 双击已存在但未勾选的字段会重新勾选；已勾选字段会高亮定位。
- 支持多段选择和多个勾选项批量移除。
- 参数和日志消息使用更明确的展开/收起图标。
- 处理器曲线支持自定义 RGB，并同步到底部图例。
- 添加处理器、字段颜色、移除全部、日志信息等窗口保持单实例。

## 兼容性

- Java 8 字节码目标。
- Windows 发布包；Ant 仍可构建跨平台 JAR、macOS App 和 Debian 包。
- 支持 PX4/APM DataFlash BIN、PX4LOG、ArduPilot 文本 LOG 和 PX4 ULog。
- 保留已有 `.fplot` 预设、参数文本、KML/GPX 和 Preferences 行为。

## 验证摘要

- 自包含格式、reader、产品、架构与本地化回归通过。
- 657MB 真实 BIN、1GiB 合成 BIN、20 个日志页和多档 DPI 回归通过。
- 干净源码可通过 PowerShell 和标准 Ant 两种方式构建。
- 发布 JAR、Windows 包和源码包可由 `package-responsive.ps1` 重建。

详细测试方法见 [TESTING.md](TESTING.md)，功能闭环见 [FEATURE_MATRIX.md](FEATURE_MATRIX.md)。
