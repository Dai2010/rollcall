# 点名助手

一个使用 Java Swing 编写的桌面点名工具，支持名单导入、名单管理、单人/多人随机抽取和连续不重复抽取。

![点名助手图标](src/main/resources/icons/rollcall-icon.png)

## 功能

- 手动输入姓名，支持全角/半角引号、分号、逗号、顿号和空格等分隔符。
- 一次导入多个 UTF-8 文本或 JSON 文件。带编号的内容会保留编号，缺少或重复编号时自动补齐。
- 名单可以添加备注、设置默认名单、增加或移除成员、删除名单。
- 单人模式和多人分组模式。多人模式可设置每组人数及组数，组数默认一组。
- 连续抽人模式记录已抽成员；剩余人数不足以完成下一组时会提示并保持当前状态。
- 名单数据显示为统一的浅蓝色圆角标签，并置于可滚动区域。

## 名单文件格式

纯文本可以每行一个姓名：

```text
张三
李四
王五
```

也可以使用编号和姓名：

```text
1,张三
2,李四
3,王五
```

JSON 支持对象数组，`id` 可省略：

```json
[
  {"id": 1, "name": "张三"},
  {"name": "李四"}
]
```

应用数据默认保存在 `~/.rollcall/lists.json`。

## 开发与构建

需要 JDK 17 或更高版本和 Maven。运行测试：

```bash
mvn test
```

构建可执行 JAR：

```bash
mvn package
java -jar target/rollcall.jar
```

所有发布构建由 GitHub Actions 执行。`Release` workflow 在 Windows runner 上使用 `jpackage` 生成 EXE 和 MSI；手动运行时填写版本标签（例如 `v0.0.2`）。

## 许可证

本项目以 GNU General Public License v3.0 发布，详见 [LICENSE](LICENSE)。
