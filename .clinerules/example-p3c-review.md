# Example P3C Review（强化版补充条款）

> 用途：作为 `example-p3c-review` skill 的补充/同步条款。skill 正文由 IDE 托管，
> 本文件用于把最新强化规约纳入版本管理，审查时与 skill 一并执行。

## 一、格式规范（强制，2026-09-17 强化）

### 1.1 禁止把多行代码压缩成一行

- 方法体**必须换行书写**：`{` 后换行，`return`/业务语句独立成行，`}` 独立成行。
- 一个方法体只有一条语句时，**同样必须展开为三行**，禁止写成
  `public int getX() { return x; }`。
- 多个语句禁止同行堆叠，禁止 `m.put("pid", p[0]); m.put("cpu", p[1]);` 这种写法。
- `try / catch / finally` 禁止单行压缩，禁止 `try { f(); } catch (Exception e) { return 0; }`。
- 字段声明禁止一行多个，禁止 `int a; String b; long c;`。
- 内部类的构造器同样必须展开为多行。

反例：

```java
public String getGroup() { return group; }
try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
if (v != null) { hasher.putString(k, UTF_8).putString(v, UTF_8);}
Socket socket; PrintWriter out; String services;
```

正例：

```java
public String getGroup() {
    return group;
}

try {
    return Integer.parseInt(s.trim());
} catch (Exception e) {
    return 0;
}

if (v != null) {
    hasher.putString(k, UTF_8).putString(v, UTF_8);
}

/** 控制连接 */
Socket socket;
/** 控制连接输出流（自动 flush） */
PrintWriter out;
/** 服务列表，形如 rdp:3389,ssh:22 */
String services;
```

### 1.2 禁止把多条注释压缩到一行

- 连续注释必须逐行展开，禁止 `// 初始化; // 校验` 式堆叠。
- Javadoc 的 `@param` / `@return` 必须各占一行，禁止与摘要行同行。

## 二、控制语句必须加大括号（强制，重申）

`if / else if / else / for / while / do-while` 语句体一律使用 `{}`。

反例：

```java
if (c == null) cache.put(key, new CachedSession());
if (running.get()) log.error("[SIP] 接受连接失败", e);
if (kv[0].equals(service)) { targetPort = Integer.parseInt(kv[1]); break; }
```

正例：

```java
if (c == null) {
    cache.put(key, new CachedSession());
}

if (running.get()) {
    log.error("[SIP] 接受连接失败", e);
}

if (kv[0].equals(service)) {
    targetPort = Integer.parseInt(kv[1]);
    break;
}
```

## 三、注释完整性（强制，重申）

1. 类必须有 Javadoc，且包含 `@author` + `@since`（本项目 `@author CH` / `@since 4.0.0.42`）。
2. 所有非 `@Override` 方法必须有 Javadoc；有入参必有 `@param`，有返回值必有 `@return`。
3. 字段必须有行内注释；内部类字段同样适用。
4. 注释一律中文；禁止 `life循环`、`处理bind异常`、`列表嵌入engines` 这类中英夹杂的机器生成措辞。
5. 禁止在方法体内部给普通语句挂 Javadoc（伪 Javadoc）；语句说明用 `//` 行注释。
6. Javadoc 必须写在注解**之前**，不得写在 `@Override` / `@GetMapping` 等注解与方法之间。

## 四、常见机器生成垃圾注释的识别特征（发现即清理）

- 重复的 `@param`（如同一参数出现 3 次以上）、`@param e e`、`@param null 空`、`@param 0 0`。
- 单个方法 Javadoc 的 `@param` 数量超过真实参数数量。
- 描述为 `X的结果`、`X信息 实例`、`扫描mode`、`空database` 等直译式英文残留。
- 挂在 `throw`、方法调用、表达式上的 Javadoc 块。

## 五、审查输出要求

1. 逐文件输出：文件路径、行号、规约编号、级别（[强制]/[推荐]/[参考]）、问题说明。
2. 优先排查压缩成一行、缺大括号、缺 Javadoc 三类问题（本文件第一至三节）。
3. 修复后必须复扫：`singleLineBody`、`multiField`、`javadoc-after-annotation`、
   重复 `@param`、超量 `@param` 五类计数应全部为 0。