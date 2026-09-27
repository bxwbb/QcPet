# QcPet

`QcPet` 是一个基于 `Paper 1.21.x` 的宠物插件，提供宠物获取、出战、跟随、重命名、洗澡、喂食、经验成长、盲盒显示、右键宠物面板，以及可配置的事件系统。

当前版本的核心目标是：

- 宠物数据持久化到 MySQL（连接失败自动降级本地文件）
- 常用数据库操作异步化，减少主线程卡顿
- 宠物跟随、名称显示、状态表现和交互面板可配置
- 宠物生命周期支持事件触发和命令联动
- 支持 PLAYER 类型宠物（基于 FancyNpcs 假玩家）

## 环境要求

- `Java 21`
- `Paper 1.21.8`
- `MySQL 8+`（可选，不装自动用本地文件存储）
- 可选依赖：`PlaceholderAPI`、`FancyNpcs`（PLAYER 类型宠物需要）

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
- **宠物骑乘**：右键宠物上马，WASD 控制，支持自动上台阶与惯性手感
- **PLAYER 类型宠物**（基于 FancyNpcs，支持皮肤）
- **MySQL 自动降级**：数据库连不上时自动切本地 YAML 文件存储
- **FancyNpcs 自动下载**：未安装时自动从 Maven 仓库下载并加载

## 安装

1. 将构建好的 `QcPet.jar` 放入服务端的 `plugins/` 目录。
2. 启动服务端，生成默认配置。
3. 配置 `plugins/QcPet/config.yml` 中的 MySQL 连接信息（可选，不配则用本地文件）。
4. 根据需要编辑 `plugins/QcPet/pet.yml`。
5. 重启服务器或执行 `/qcpet reload`。

> 如果使用 PLAYER 类型宠物，QcPet 会自动检测并下载 FancyNpcs 插件，无需手动安装。

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

```yml
config-version: 2

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
  feed:
    min-times-per-day: 3
    max-times-per-day: 5
  ride:
    ground-speed: 0.55        # 地面骑乘最大速度（格/ tick）
    ground-accel: 0.35        # 加速度系数（0~1），越大提速越快
    ground-friction: 0.55     # 摩擦系数（0~1），越大滑行越久
    flying-speed: 0.45        # 飞行骑乘水平速度
    flying-vertical-speed: 0.2
    flying-glide-factor: 0.35
```

> MySQL 连接失败时自动降级到 `plugins/QcPet/data/` 下的 YAML 文件存储，无需额外配置。

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

  # PLAYER 类型宠物（需要 FancyNpcs）
  companion:
    type: PLAYER
    displayName: "&a勇者伙伴"
    rarity: "&b珍稀"
    times: 1.1
    levelExpRequirement: "110 + (%1% * 22)"
    skin: "Notch"           # 玩家名 / UUID / 皮肤图片 URL
    skinVariant: AUTO       # AUTO 自动 / SLIM 纤细
    events:
      on-spawn:
        - "[console] tellraw %owner% {\"text\":\"勇者伙伴来到了你身边\",\"color\":\"green\"}"
```

`modelId` 用于给最终宠物名称添加最高优先级的模型前缀。
例如 `modelId: "ABC"` 时，最终名称会变成 `@cet_ABC@原名称`；为空时不添加前缀。

### PLAYER 类型宠物

当 `type: PLAYER` 时，宠物使用 FancyNpcs 生成假玩家实体：

- `skin`：皮肤标识符，支持玩家名、UUID、皮肤图片 URL
- `skinVariant`：皮肤模型（`AUTO` 自动适配 / `SLIM` 纤细手臂）
- 宠物不进入 Tab 列表，不可碰撞，不限制可见距离
- 右键宠物同样打开 GUI 面板

> FancyNpcs 未安装时 QcPet 会自动下载所需版本（当前 2.9.2）。

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

- 宠物达到配置时间后会进入"想洗澡"状态
- 脏状态会显示粒子和名称后缀
- 洗澡后会播放水声、泡泡效果、爱心效果

### 喂食

- 宠物每天会按配置进入 `3` 到 `5` 次饥饿状态
- 饥饿状态会显示粒子和名称后缀
- 喂食后会播放进食音效和爱心效果

## 跟随与实体行为

- 地面宠物会尝试跟随到主人附近约 `1` 格位置
- 飞行宠物目标点为主人 `Y + 3`
- PLAYER 类型宠物通过 packet 传送跟随
- 宠物无敌
- 宠物被攻击会取消伤害

## 骑乘系统

### 上下马

- **右键宠物**：骑上去（需宠物类型支持骑乘，且玩家未在面板中关闭该宠物的骑乘开关）
- **潜行右键宠物**：打开宠物面板（不骑马）
- **按住 Shift**：原版下车

### 两层骑乘开关

| 层级 | 配置位置 | 作用 |
| --- | --- | --- |
| 类型级 | `pet.yml` 中 `rideable: true/false` | 这种宠物类型是否支持骑乘 |
| 实例级 | 宠物面板内的"允许/禁止骑乘"按钮 | 玩家自己开关当前这只宠物 |

两层都为 `true` 时才能骑。实例级开关会持久化到宠物数据中，下线重登保留。

### 骑乘移动

骑乘后用 WASD 控制方向，插件每 tick 读取玩家输入并驱动宠物实体：

- **加速**：按下方向键时速度逐渐提升到最大值，不会瞬间满速
- **滑行**：松开方向键后速度按摩擦系数衰减，有惯性
- **上台阶**：贴脸撞 1 格方块时会自动小跳越过去，不会被墙卡住
- **垂直**：空中垂直速度交给原版重力，正常加速下落
- **飞行宠物**：空格上升、不按则缓慢滑翔

手感参数在 `config.yml` 的 `pet.ride` 段可调：

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `ground-speed` | `0.55` | 地面最大速度 |
| `ground-accel` | `0.35` | 加速度系数（1.0 = 瞬间满速） |
| `ground-friction` | `0.55` | 摩擦系数（1.0 = 永不减速） |
| `flying-speed` | `0.45` | 飞行水平速度 |
| `flying-vertical-speed` | `0.2` | 飞行垂直速度 |
| `flying-glide-factor` | `0.35` | 飞行滑翔下降比例 |

> 调整 `ground-accel` / `ground-friction` 后 `/qcpet reload` 即可生效，无需重启。

## 数据存储

支持两种存储后端：

1. **MySQL**（默认）：连接 `config.yml` 中配置的数据库
2. **本地文件**（自动降级）：MySQL 连接失败时自动启用，数据存于 `plugins/QcPet/data/`

宠物的部分动态状态会写入 `Pet.data`，例如上次洗澡时间、上次喂食时间、名称元数据等。

## 开发说明

- 项目主逻辑位于 `src/main/java/org/bxwbb/qcpet`
- 关键模块：
  - `pet/` 宠物核心逻辑
  - `command/` 命令处理
  - `gui/` 宠物界面
  - `event/` Bukkit 事件监听
  - `utils/` 持久化与工具类
  - `utils/saveUtil/` 存储层（`PetStorage` 接口 + MySQL / 本地文件两种实现）

## 注意事项

- `on-tick` 每刻触发，配置不当会产生明显负载
- 事件命令是直接执行的，避免在高频事件里写重命令
- 如果启用 PlaceholderAPI，宠物名字与部分显示会先经过 PAPI 解析
- 修改 `pet.yml` 后建议执行 `/qcpet reload` 或重启服务端
- PLAYER 类型宠物需要 FancyNpcs 2.9.x（Java 17 编译，兼容 Java 21 服务器）
