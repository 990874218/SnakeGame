# 🐍 Snake Game - Android

一个使用 Jetpack Compose 开发的经典贪吃蛇游戏 Android 应用。

## 📱 项目简介

这是一个现代化的贪吃蛇游戏实现，采用 Kotlin 和 Jetpack Compose 构建，提供了流畅的游戏体验和精美的用户界面。

## ✨ 功能特性

- 🎮 **经典游戏玩法** - 控制蛇吃食物，避免撞墙和撞到自己
- 👆 **滑动控制** - 通过滑动手势控制蛇的移动方向
- 🔊 **音效反馈** - 吃到食物和游戏结束时的音效提示
- 📳 **震动反馈** - 游戏事件触发的震动反馈
- 📊 **分数系统** - 实时显示当前分数和最高分记录
- 💾 **数据持久化** - 自动保存最高分记录
- ⏸️ **暂停功能** - 支持暂停和继续游戏
- 🎨 **自适应布局** - 根据屏幕尺寸自动调整网格大小
- 🖥️ **大屏优化** - 针对大屏幕设备优化网格绘制性能
- ✨ **视觉效果** - 吃到食物时的闪烁动画效果

## 🛠️ 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **最低 SDK**: Android 7.0 (API 24)
- **目标 SDK**: Android 14 (API 36)
- **设计规范**: Material 3

## 📋 系统要求

- Android 7.0 (API 24) 或更高版本
- 支持触摸屏的设备

## ⬇️ 下载与安装

- 从 Releases 下载最新 APK：[@Releases 页面](https://github.com/990874218/SnakeGame/releases)
- 升级安装说明：
  - 相同包名（applicationId：`com.example.snakegame`）
  - 相同签名证书
  - 递增的 versionCode（本项目会自动用 Git 提交数作为 versionCode）
  满足以上条件，直接覆盖安装不会丢失数据（最高分会保留）。

## 🚀 构建和运行

### 前置要求

- Android Studio Hedgehog 或更高版本
- JDK 11 或更高版本
- Android SDK

### 构建步骤

1. 克隆仓库：
```bash
git clone https://github.com/990874218/SnakeGame.git
cd SnakeGame
```

2. 使用 Android Studio 打开项目

3. 同步 Gradle 依赖

4. 连接 Android 设备或启动模拟器

5. 点击运行按钮或使用命令：
```bash
./gradlew installDebug
```

## 🎮 游戏操作

- **滑动控制**: 在屏幕上滑动手指来控制蛇的移动方向
  - 向上滑动：向上移动
  - 向下滑动：向下移动
  - 向左滑动：向左移动
  - 向右滑动：向右移动

- **暂停游戏**: 点击屏幕顶部的暂停按钮

- **游戏结束**: 当蛇撞到墙壁或自己的身体时，游戏结束

## 📁 项目结构

```
SnakeGame/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/snakegame/
│   │       │   ├── SnakeGameScreen.kt    # 主游戏界面
│   │       │   ├── GameCanvas.kt         # 游戏画布绘制
│   │       │   ├── GameConfig.kt         # 游戏配置
│   │       │   └── ...                   # 其他游戏逻辑文件
│   │       ├── res/                       # 资源文件
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── README.md
```

## 📝 许可证

本项目采用 MIT 许可证。


## 📧 联系方式

如有问题或建议，请通过 GitHub Issues 联系。

---

**享受游戏！** 🎉

