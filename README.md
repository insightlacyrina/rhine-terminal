# Rhine Terminal

Rhine Terminal 是 Android 动态壁纸项目，提供独立的锁屏与主屏壁纸。锁屏使用 400 片实时渲染的鳍片雕塑，主屏提供暖白样本盒、石墨样本盒和带本机巡检数据的石墨终端。

## 使用

1. 打开应用，进入「设置 → 个性化 → 锁屏称呼」修改锁屏中英文提示的称呼。留空恢复默认的 `ZSY`。
2. 在「设置 → 个性化 → 排版模式」选择平板模式或手机模式。手机模式按一加 13 的 19.8:9 比例排布锁屏和主屏，支持横竖屏。
3. 在「壁纸」中选择锁屏或主屏。点「预览」查看效果，点「应用」打开 Android 壁纸选择器。
4. 「主题音效」开启后使用上游界面音效，默认关闭。静音、震动、勿扰、通话和其它媒体播放时保持安静。
5. 「5 秒闲置黑化」默认开启。锁屏可见且壁纸收到的交互停止 5 秒后转黑，前 0.5 秒有瞬时扰动，随后保持平静并常显 SESSION IDLE 状态弹窗。触摸或轻敲恢复浅色、收起弹窗并重新计时。

认证由系统锁屏处理。人脸、指纹和短触黑化是应用内演示。系统 PIN／密码失败变色是单独授权的可选功能。锁屏雕塑响应系统转发的触摸，另有基于加速度计的轻敲触发路径。系统安全输入层中的触摸或认证尝试可能无法重置壁纸的闲置计时。显示刷新率由设备和系统决定。

锁屏左侧无边框信息流显示本机内存、供电、电池温度、热状态、刷新率、壁纸提交帧率和运行时间。设备快照每秒采样，滚动纹理独立更新。右侧 OPERATOR 模块使用自定义称呼，整体中心与屏幕纵向中心对齐。深浅样本盒共用低对比稀疏场线与分子片段背景。

## 构建

使用 JDK 17、Android SDK 35 和仓库自带的 Gradle Wrapper。

```sh
./gradlew assembleDebug
```

输出位于 `app/build/outputs/apk/debug/app-debug.apk`。测试与离线预览生成步骤见 [构建说明](README.build.md)。

## 许可与来源

项目按 [GNU AGPL-3.0-or-later](LICENSE) 发布。鳍片、动效及部分素材移植自 [Rhine Lock](https://github.com/tmc5csxr9m-cpu/rhine-lock/tree/a4df68c6bd9e01ecb7660659becf495957e57faa)。内置的 Montserrat 和 JetBrains Mono 字体使用各自的 SIL OFL 1.1 许可，原始许可随字体附带。改编及素材出处详见 [NOTICE](app/src/main/assets/rhine-lock/NOTICE.txt)。

壁纸图卡插画使用 ImageGen 生成，界面控件由 Android 原生视图绘制。

28 个界面音效逐字节复用上游，包含其 AGPL 合成音和 Kenney CC0 音效，许可及文件清单见 [音效来源](app/src/main/assets/rhine-lock/audio/NOTICE.txt)。
