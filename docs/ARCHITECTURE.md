# 架构与数据流

## 目录职责

```text
src/me/drton/flightplot/      Swing 应用、图表、会话状态和导出入口
src/me/drton/flightplot/processors/
                              可组合的数据处理器和数学工具
src/me/drton/flightplot/export/
                              图片之外的 KML/GPX 航迹读取与写入
jMAVlib/src/me/drton/jmavlib/log/
                              BIN、文本 LOG、ULog 和其他日志 reader
src/net/objecthunter/exp4j/   内嵌表达式求值兼容代码
src/org/json/                 内嵌预设 JSON 兼容代码
tests/                        无测试框架依赖的可执行回归类
packaging/                    macOS、Ubuntu 和 Arch 打包资源
lib/                          构建与运行所需的历史兼容依赖
```

## 核心对象

- `FlightPlot`：程序入口和主窗口装配；把按钮、菜单、拖放与命令行统一接到打开、处理器和导出动作。
- `Workspace`：唯一的日志页集合和当前页指针；负责添加、选择、关闭和查重。
- `ChartTab`：单个日志页的 reader、图表、数据集、处理器表、时间范围、消息和参数。
- `LogService`：扩展名与内容探测、reader 创建、空文件检查、消息转换和取消检查。
- `ProcessorSelectionMemory`：按日志格式保存处理器配置、勾选状态和颜色，并在新文件存在对应字段时恢复。
- `DialogCoordinator`：登记所有临时窗口，同一动作只允许一个窗口，并在关闭后释放登记。
- `FieldsPanel`：只消费 reader 返回的字段 map，负责分组、搜索、自然排序和字段选择事件。
- `PX4LogReaderOptimized`：为大 DataFlash BIN 建索引并按时间 seek；只读取当前处理器需要的字段。

## 打开日志

```mermaid
flowchart LR
    A[工具栏/菜单/拖放/命令行] --> B[FlightPlot 统一打开入口]
    B --> C[LogService 格式探测]
    C --> D[后台创建 LogReader]
    D --> E[读取动态 schema/参数/消息]
    E --> F[EDT 提交到 Workspace]
    F --> G[创建或替换 ChartTab]
    G --> H[恢复同格式选择]
    H --> I[按需读取并绘图]
```

每次打开任务都有版本标识。新任务、关闭页或取消会让旧结果失效；旧任务即使稍后完成，也只关闭自己的 reader，不再修改界面。

## 绘图刷新

1. 处理器表确定当前需要的原始字段。
2. reader 的 `setNeededFields` 限制解析范围。
3. 后台任务按当前横轴范围 seek 和读取。
4. 长序列采用分桶保留最小值与最大值，避免普通抽样漏掉尖峰。
5. 只有仍属于当前页和当前版本的结果才提交到 EDT。
6. `ChartTab` 更新数据集、图例、标记和分钟线。

## 状态边界

- `Workspace` 拥有页；`ChartTab` 拥有页内状态；主窗口组件只是当前页的视图。
- 处理器表的勾选值是曲线可见性的唯一来源。
- 字段颜色保存在处理器配置和选择记忆中，不由图表 renderer 反向推导。
- 字段本地化不参与字段查找、处理器参数或格式签名。
- 所有 Swing 修改发生在 EDT；日志 I/O、序列计算和导出发生在后台。
- 临时窗口关闭必须经过 `DialogCoordinator`，避免遗留引用和重复模态循环。
