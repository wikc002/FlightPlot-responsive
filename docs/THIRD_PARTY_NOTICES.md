# 第三方组件与许可证说明

本文件用于记录 0.5.8 源码和发布包中直接包含的上游代码与二进制依赖。各组件继续遵循自己的许可证；根目录 `LICENSE` 适用于当前维护者拥有权利的 0.5.8 改动。

| 组件 | 位置 | 用途 | 许可证/说明 |
|---|---|---|---|
| PX4/FlightPlot | `src/me/drton/flightplot` 的历史基础 | FlightPlot 桌面应用 | 项目来源为 `PX4/FlightPlot`，其仓库由 `DrTon/FlightPlot` 派生；请同时查阅对应上游仓库历史和文件声明 |
| jMAVlib | `jMAVlib/src`、`jMAVlib/README.md` | PX4/APM/ULog 读取与 MAVLink 工具 | BSD 3-Clause，Copyright (c) 2014 Anton Babushkin |
| exp4j | `src/net/objecthunter/exp4j` | 表达式处理器 | Apache License 2.0；版权与许可头保留在各源码文件中 |
| JSON-java | `src/org/json` | `.fplot` 预设 JSON | JSON License；版权与许可文本保留在各源码文件中，含“Good, not Evil”条款 |
| JFreeChart 1.0.14 | `lib/jfreechart-1.0.14.jar` | 图表绘制 | GNU LGPL 2.1 或该组件发布包声明的后续版本 |
| JCommon 1.0.17 | `lib/jcommon-1.0.17.jar` | JFreeChart 公共支持 | GNU LGPL 2.1 或该组件发布包声明的后续版本 |
| Java 3D Vecmath | `lib/vecmath.jar` | 四元数和向量数学 | 遵循该二进制发行版内/上游发布的许可证 |
| JarBundler 2.4.0 | `lib/jarbundler-2.4.0.jar` | Ant 的 macOS App 打包目标 | 仅构建时使用；遵循 JarBundler 上游许可证 |
| JDOM | `lib/jdom.jar` | 历史打包/兼容路径 | 仅构建兼容依赖；遵循 JDOM 上游许可证 |
| universalJavaApplicationStub | `lib/universalJavaApplicationStub` | macOS 启动桩 | 遵循上游项目许可证 |

## 随仓库文件校验值

| 文件 | SHA-256 |
|---|---|
| `jarbundler-2.4.0.jar` | `B9A588DA66638015D45F085348B594E5A2426FED36E40DB2141990EA6865C0E6` |
| `jcommon-1.0.17.jar` | `1E9D04DF09E938058C946B5DFD2C3D765329D1D11E87FFAE6AA75A19358B23BF` |
| `jdom.jar` | `9259D44FB5C92C4E9D7C7014473A1DD7FDBD40D86B0E78BBC42420F323013508` |
| `jfreechart-1.0.14.jar` | `E268FFF64BD3B94A42537ECFAE979FEE397891E60CABABE178A6564760931A0F` |
| `universalJavaApplicationStub` | `29157324DDFB3DAE572FB14D17707905AE4970135EF521ECA40317F1ACA2C485` |
| `vecmath.jar` | `C13473C1AEE6F583424D87009A949754FA21A257E2C8484EEFDF1EFA031EB2E0` |

## 来源链接

- PX4 FlightPlot: https://github.com/PX4/FlightPlot
- DrTon FlightPlot: https://github.com/DrTon/FlightPlot
- jMAVlib: https://github.com/PX4/jMAVlib
- exp4j: https://www.objecthunter.net/exp4j/
- JSON-java: https://github.com/stleary/JSON-java
- JFreeChart: https://www.jfree.org/jfreechart/

## 发布注意

- 不得删除内嵌源码文件中的版权和许可头。
- 二进制发布包应随附本文件和根目录 `LICENSE`。
- 升级或替换 `lib/` 中的文件时，应记录精确版本、许可证和校验值，并重新执行构建与回归。
- 上游 FlightPlot 仓库当前未在根目录展示单独的 `LICENSE` 文件；发布者需要自行确认对历史 FlightPlot 源码的再许可权限。0.5.8 的 BSD 3-Clause 声明不会覆盖发布者无权再许可的第三方部分。
