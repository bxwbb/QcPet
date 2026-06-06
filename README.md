# QcPet

`QcPet` 是一个基于 `Paper 1.21.x` 的宠物插件，提供宠物获取、出战、跟随、重命名、洗澡、喂食、经验成长、盲盒显示、右键宠物面板，以及可配置的事件系统。

当前版本的核心目标是：

- 宠物数据持久化到 MySQL
- 常用数据库操作异步化，减少主线程卡顿
- 宠物跟随、名称显示、状态表现和交互面板可配置
- 宠物生命周期支持事件触发和命令联动

## 环境要求

- `Java 21`
- `Paper 1.21.8`
- `MySQL 8+`
- 可选依赖：`PlaceholderAPI`

## 已实现功能

- 宠物获取、删除、显示、隐藏、选择
- 宠物右键打开 GUI 面板
- 宠物重命名，支持 `&` 颜色代码
- 名称支持 `%player%`、`%qcpet_*%` 和 PlaceholderAPI
- 宠物经验与等级成长
- `0` 级宠物盲盒态显示
- 宠物洗澡系统
- 宠物喂食系统
- 宠物受伤保护、无敌、不可碰撞
- 宠物上线自动恢复显示
- 宠物生命周期事件系统
- 宠物命令 Tab 补全

## 安装

1. 将构建好的 `QcPet.jar` 放入服务端的 `plugins/` 目录。
2. 启动服务端，生成默认配置。
3. 配置 `plugins/QcPet/config.yml` 中的 MySQL 连接信息。
4. 根据需要编辑 `plugins/QcPet/pet.yml`。
5. 重启服务器或执行 `/qcpet reload`。

## 命令

主命令为 `/qcpet`，别名：`/pet`、`/pets`。

### 玩家命令

- `/qcpet help`
- `/qcpet list`
- `/qcpet select`
- `/qcpet show <宠物ID>`
- `/qcpet hide <宠物ID>`
- `/qcpet bath <宠物ID>`
- `/qcpet feed <宠物ID>`
- `/qcpet info <宠物ID>`

### 管理命令

- `/qcpet reload`
- `/qcpet give <宠物模板名>`
- `/qcpet remove <宠物ID>`
- `/qcpet addexp <宠物ID> <数值>`
- `/qcpet addlevel <宠物ID> <数值>`
- `/qcpet storage`

## 权限

### 聚合权限

- `qcpet.*`
- `qcpet.user`
- `qcpet.admin`

### 细分权限

- `qcpet.command`
- `qcpet.command.help`
- `qcpet.command.list`
- `qcpet.command.select`
- `qcpet.command.show`
- `qcpet.command.hide`
- `qcpet.command.bath`
- `qcpet.command.feed`
- `qcpet.command.info`
- `qcpet.command.reload`
- `qcpet.command.give`
- `qcpet.command.remove`
- `qcpet.command.addexp`
- `qcpet.command.addlevel`
- `qcpet.command.storage`
- `qcpet.bypass.limit`
- `qcpet.bypass.world`

## 配置文件

### `config.yml`

当前包含以下主要配置：

```yml
config-version: 1

save:
  sql:
    url: jdbc:mysql://127.0.0.1:3306/qcpet?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    user: root
    password: password
    table: qcpet

player:
  max-display-count: 1

pet:
  rename:
    max-length: 32
    allow-color-codes: true
    invalid-pattern: "[\\r\\n\\t]"
  bath:
    interval-hours: 24
    reward-exp: 25
  feed:
    min-times-per-day: 3
    max-times-per-day: 5
```

### `pet.yml`

`pet.yml` 用于定义宠物模板、默认附加配置、事件和元数据。

结构示例：

```yml
default:
  type: BEE
  displayName: "%qcpet_key%"
  modelId: ""
  times: 1
  levelExpRequirement: "100 + (%1% * 25)"
  metaData: {}
  events:
    on-give:
      - "[console] tellraw %owner% {\"text\":\"你获得了宠物 %qcpet_key%\",\"color\":\"gold\"}"

defaultAdd:
  +displayName: "★ %qcpet_owner_name% 的 "
  displayName+: " &r★"
  times*: 1
  times+: 0
  metaData:
    isBaby: true
    bathNeedPrefix: ""
    bathNeedSuffix: " 🛁"
    feedNeedPrefix: ""
    feedNeedSuffix: " 🍖"

pets:
  wolf:
    type: WOLF
    displayName: "&f守卫犬"
    modelId: ""
    times: 1.2
    levelExpRequirement: "100 + (%1% * 20)"
    metaData:
      angry: false
    events:
      on-spawn:
        - "[console] tellraw %owner% {\"text\":\"守卫犬已到位\",\"color\":\"yellow\"}"
```

`modelId` 用于给最终宠物名称添加最高优先级的模型前缀。
例如 `modelId: "ABC"` 时，最终名称会变成 `@cet_ABC@原名称`；为空时不添加前缀。

## 宠物名称与占位符

### 常用名称占位符

- `%player%`
- `%player_name%`
- `%qcpet_key%`
- `%qcpet_type%`
- `%qcpet_id%`
- `%qcpet_name%`
- `%qcpet_level%`
- `%qcpet_exp%`
- `%qcpet_times%`
- `%qcpet_owner_name%`

### 元数据占位符

- `%qcpet_metadata_key_<key>%`
- `%qcpet_metadata_value_<value>%`

示例：

```text
%qcpet_metadata_key_bathNeedSuffix%
%qcpet_metadata_value_true%
```

## 事件系统

每个宠物模板都可以定义 `events`。事件值为命令列表。

支持两种执行者前缀：

- `[console]` 以控制台执行
- `[player]` 以宠物主人执行

如果不写前缀，默认按控制台执行。

### 支持的事件名

- `on-give`
- `on-spawn`
- `on-hide`
- `on-join-show`
- `on-bath`
- `on-feed`
- `on-rename`
- `on-add-exp`
- `on-add-level`
- `on-level-up`
- `on-damage`
- `on-second`
- `on-tick`

### 事件额外占位符

- `%owner%`
- `%player%`
- `%target_player%`
- `%qcpet_old_level%`
- `%qcpet_new_level%`
- `%qcpet_event_player%`
- `%qcpet_event_owner%`
- `%qcpet_event_pet_id%`
- `%qcpet_event_pet_type%`
- `%qcpet_event_pet_level%`
- `%qcpet_event_pet_exp%`
- `%qcpet_event_pet_name%`
- `%qcpet_event_world%`
- `%qcpet_event_x%`
- `%qcpet_event_y%`
- `%qcpet_event_z%`
- `%qcpet_event_uuid%`
- `%qcpet_event_target_uuid%`
- `%qcpet_event_target_type%`
- `%qcpet_event_random%`

### 事件示例

```yml
events:
  on-give:
    - "[console] tellraw %owner% {\"text\":\"你获得了宠物 %qcpet_name%\",\"color\":\"gold\"}"
  on-spawn:
    - "[console] playsound minecraft:entity.allay.ambient master %owner% ~ ~ ~ 1 1.2"
  on-hide:
    - "[console] tellraw %owner% {\"text\":\"宠物已收起\",\"color\":\"gray\"}"
  on-level-up:
    - "[console] say %owner% 的宠物从 %qcpet_old_level% 升到了 %qcpet_new_level%"
  on-damage:
    - "[console] tellraw %owner% {\"text\":\"宠物受到了攻击，来源：%target_player%%qcpet_event_target_type%\",\"color\":\"red\"}"
  on-second:
    - "[console] title %owner% actionbar {\"text\":\"%qcpet_name% Lv.%qcpet_level%\",\"color\":\"yellow\"}"
```

## GUI 面板

右键自己的宠物会打开宠物面板。

当前面板提供：

- 宠物展示区域
- 当前经验与等级进度
- 重命名入口
- 隐藏入口
- 洗澡入口
- 喂食入口

## 盲盒机制

- 新获取的宠物默认是 `0` 级
- `0` 级宠物名称显示为 `???`
- `0` 级宠物类型和部分属性隐藏
- `0` 级宠物经验仍然显示
- `0` 级宠物不会进入洗澡和饥饿状态
- `0` 级宠物当前显示为一个文字展示实体 `???`

## 洗澡与喂食

### 洗澡

- 宠物达到配置时间后会进入“想洗澡”状态
- 脏状态会显示粒子和名称后缀
- 洗澡后会播放水声、泡泡效果、爱心效果
- 洗澡后会获得 `config.yml` 中配置的经验奖励

### 喂食

- 宠物每天会按配置进入 `3` 到 `5` 次饥饿状态
- 饥饿状态会显示粒子和名称后缀
- 喂食后会播放进食音效和爱心效果

## 跟随与实体行为

- 地面宠物会尝试跟随到主人附近约 `1` 格位置
- 飞行宠物目标点为主人 `Y + 3`
- 宠物无敌
- 宠物不会伤害主人
- 宠物被攻击会取消伤害
- 宠物与主人不可碰撞

## 数据存储

当前数据存储使用 MySQL。

宠物的部分动态状态会写入 `Pet.data`，例如：

- 上次洗澡时间
- 上次喂食时间
- 名称前后缀相关元数据

## 开发说明

- 项目主逻辑位于 `src/main/java/org/bxwbb/qcpet`
- 关键模块：
  - `pet/` 宠物核心逻辑
  - `command/` 命令处理
  - `gui/` 宠物界面
  - `event/` Bukkit 事件监听
  - `utils/` 持久化与工具类

## 注意事项

- `on-tick` 每刻触发，配置不当会产生明显负载
- 事件命令是直接执行的，避免在高频事件里写重命令
- 如果启用 PlaceholderAPI，宠物名字与部分显示会先经过 PAPI 解析
- 修改 `pet.yml` 后建议执行 `/qcpet reload` 或重启服务端
