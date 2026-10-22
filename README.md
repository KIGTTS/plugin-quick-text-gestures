# 手势触发

在大字幕区域画出 M、W、Z、闪电、沙漏或折钩，即可发送绑定的快捷文本。

设置 → 内容 → 手势触发：先选择已有的快捷文本，再开启对应手势。支持文字、音频、媒体和网页卡片，发送行为与直接点击词条一致。被绑定词条删除后，对应手势自动停用并恢复为空；重新创建同名词条不会自动绑定。

点按编辑、长按全屏与双指调整字号保留。启用绑定后，大字幕区域优先接收绘制手势，区域外仍可侧滑打开主页抽屉。手势只在文字大字幕的前台显示区识别。

需要 API18 主程序、JDK17 与 Android SDK36。构建：`./gradlew.bat testDebugUnitTest assembleDebug collectPluginDependencies`。签名包：`./Package.ps1 -SigningKey <发布者私钥> -AndroidSdk <SDK路径>`。

GPL-3.0-only；许可与原项目声明见 LICENSE 和 NOTICE.md。
