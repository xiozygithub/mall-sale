<h1>用户系统</h1>
接口文档地址：http://127.0.0.1:8080/user/doc.html

## 简历项目描述

- 1、使用多线程进行用户分表数据迁移，提高性能
- 2、使用sharding-jdbc进行分库分表
- 3、一套通用的分表数据迁移方案，包含 数据对比，数据迁移，数据验证，数据修复等功能
-

## 技术栈

- Mysql
- Redis
- RabbitMQ
- Guava
- Sharding JDBC

## 启动命令：

`-Xms7g -Xmx7g -Xmn2g -Xss256K -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -XX:+UseCompressedOops -XX:+UseConcMarkSweepGC -XX:CMSInitiatingOccupancyFraction=75 -XX:+UseCMSInitiatingOccupancyOnly -XX:MaxTenuringThreshold=6 -XX:+ExplicitGCInvokesConcurrent -XX:+ParallelRefProcEnabled -XX:CMSFullGCsBeforeCompaction=10`