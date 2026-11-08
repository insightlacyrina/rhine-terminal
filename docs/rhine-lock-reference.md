# Rhine Lock 参考记录

0.8.0 对齐上游的单次拒绝撕裂时序，保持 300 毫秒后按 14/s 衰减；雕塑扰动按 3.2/s 接近 0.12，不再叠加额外镜头冲击。背景与雕塑共用同一份数字撕裂着色器，静止黑化不持续运行后处理。黑色鳍片增加宽软箱与 Fresnel 反射，以移动端解析光照近似上游环境光。

界面身份模块从 PersonnelPanel 的 OPERATOR、SESSION LOCKED 与姓名条派生。用户选定开放档案式布局，中心位于屏幕纵向 50%，姓名读取本机设置。左侧无框信息流使用本机遥测，固定保留 12 行，系统快照每秒采样；独立小纹理每秒至多刷新 30 次。深浅样本背景改为稀疏场线和两个独立分子片段，120 秒循环。

音效直接复用该快照的 28 个 WAV，文件逐字节相同，音量曲线、随机音色和入场、验证、拒绝、恢复、退场时序按上游移植。许可及逐项来源见 [音效清单](../app/src/main/assets/rhine-lock/audio/NOTICE.txt)。闲置 5 秒黑化属于 Android 壁纸的独立视觉行为，触摸恢复，噪声在进入后 0.5 秒内结束。此后常显 SESSION IDLE，使用与拒绝提示共用的纯黑底、细橙框及排版。竖屏状态弹窗放在身份模块下方，横屏沿用中央位置。安全输入层未转发的交互无法重置该计时。

用户提供的项目快照为提交 `a4df68c6bd9e01ecb7660659becf495957e57faa`。其 Wayland 前端用 Three.js 实例化程序几何、PBR 光照和后处理。0.6.1 将 `gradeFragment` 的随机函数、分带撕裂、块扰动、RGB 偏移与扫描噪声移植到 Android GLES 及电脑 WebGL 预览。先保留约 185 毫秒的整组冲击，再启动分带撕裂。安卓系统认证继续由 One UI 处理。[上游着色器](https://github.com/tmc5csxr9m-cpu/rhine-lock/blob/a4df68c6bd9e01ecb7660659becf495957e57faa/frontend/src/lib/scene/shaders.ts)

0.7.0 将上游 400 片鳍片的 ∞ 曲线、切角、连续输运、组装和剥离移植为 Android GLES 的单次实例化绘制。横竖屏沿用各自的构图，光照用移动端近似，背景颜色按上游暖纸和石墨色调整。[上游场景](https://github.com/tmc5csxr9m-cpu/rhine-lock/blob/a4df68c6bd9e01ecb7660659becf495957e57faa/frontend/src/lib/scene/Stage.ts)

上游的桌面位图是演示用截图，主屏设计取自其实际渲染的磨砂卡片流。暖白和石墨主屏共用五张适配壁纸尺寸的立体卡片，采用上游 `card.png`、104:74.88:3.2 比例、圆角裁切和朝向公式。软箱方向取自上游生成器，阴影、反射及状态栏留白在 GLES 中计算。石墨终端在卡片之上叠加本机巡检层。锁屏和主屏都复用上一版上游字体、烟雾与蜂巢素材。APK 与源码包内附上游 AGPL、字体 OFL 和复用清单。[上游素材来源](https://github.com/tmc5csxr9m-cpu/rhine-lock/blob/a4df68c6bd9e01ecb7660659becf495957e57faa/tools/assets/README.md) · [复用清单](../app/src/main/assets/rhine-lock/NOTICE.txt)

系统级生物识别事件的 Android 状态监听要求 `USE_BIOMETRIC_INTERNAL`。普通壁纸使用可独立测试的人脸与指纹演示。真实凭据失败仍走原有可选的设备管理员事件。[Android 生物识别状态监听](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/biometrics/BiometricManager.java) · [设备凭据失败事件](https://developer.android.com/reference/android/app/admin/DeviceAdminReceiver#ACTION_PASSWORD_FAILED)
