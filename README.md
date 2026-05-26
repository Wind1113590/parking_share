# 共享停车位预约调度平台

本项目提供基于 Docker Compose 的一键环境，包含以下中间件：

- **MySQL 8.0+**：关系数据库
- **Redis Stack Server**：缓存 + 向量索引
- **RabbitMQ 3.13.1**：消息队列
- **Elasticsearch 8.18.8**：全文检索（可选）

> 所有服务均使用官方镜像，开箱即用。

---

## 一、快速启动

### 1. 创建 `docker-compose.yml`

将以下内容保存为 `docker-compose.yml` 文件：

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: parking-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: parking
      MYSQL_CHARACTER_SET_SERVER: utf8mb4
      MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - parking-net

  redis:
    image: redis/redis-stack-server:latest
    container_name: parking-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - parking-net

  rabbitmq:
    image: rabbitmq:3.13.1-management
    container_name: parking-rabbitmq
    restart: always
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    networks:
      - parking-net

  elasticsearch:
    image: elasticsearch:8.18.8
    container_name: parking-es
    restart: always
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    networks:
      - parking-net

volumes:
  mysql_data:
  redis_data:
  rabbitmq_data:
  es_data:

networks:
  parking-net:
    driver: bridge
```

### 2. 准备数据库初始化脚本 `init.sql`
将下面的所有建表语句复制到一个名为 init.sql 的文件中，放在与 docker-compose.yml 相同的目录下。MySQL 容器首次启动时会自动执行该脚本。

```sql

CREATE DATABASE IF NOT EXISTS parking;
USE parking;

-- 用户表
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `role` tinyint NOT NULL DEFAULT '0' COMMENT '0普通用户 1业主',
  `balance` decimal(10,2) DEFAULT '0.00' COMMENT '钱包余额（业主收入+用户预充值）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `password` varchar(128) NOT NULL DEFAULT '' COMMENT 'bcrypt加密后的密码',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_phone` (`phone`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 车位表
CREATE TABLE `parking_slot` (
  `id` bigint NOT NULL,
  `owner_id` bigint NOT NULL COMMENT '业主用户ID',
  `address` varchar(255) NOT NULL,
  `latitude` decimal(10,8) NOT NULL COMMENT '纬度',
  `longitude` decimal(11,8) NOT NULL COMMENT '经度',
  `base_price_per_hour` decimal(10,2) NOT NULL COMMENT '基础小时价格（元）',
  `status` tinyint DEFAULT '1' COMMENT '1正常 0停用',
  `start_time` time DEFAULT '08:00:00',
  `end_time` time DEFAULT '20:00:00',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `time_slice` (
  `id` bigint NOT NULL,
  `slot_id` bigint NOT NULL,
  `slice_date` date NOT NULL COMMENT '日期',
  `start_minute` int NOT NULL COMMENT '从0点开始的分钟数，如 480 = 08:00',
  `end_minute` int NOT NULL COMMENT '结束分钟，如 495 = 08:15',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0空闲 1锁定中(临时) 2已预约 3不可用(业主关闭)',
  `order_id` bigint DEFAULT NULL COMMENT '锁定/预约的订单ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slot_date_minute` (`slot_id`,`slice_date`,`start_minute`),
  KEY `idx_slot_date_status` (`slot_id`,`slice_date`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 订单表
CREATE TABLE `order` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `slot_id` bigint NOT NULL,
  `start_time` datetime NOT NULL COMMENT '预约开始时间',
  `end_time` datetime NOT NULL,
  `total_amount` decimal(10,2) NOT NULL COMMENT '总金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待支付 1已支付 2使用中 3已完成 4已取消 5已退款',
  `pay_time` datetime DEFAULT NULL,
  `cancel_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `actual_end_time` datetime DEFAULT NULL,
  `actual_amount` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_slot_time` (`slot_id`,`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 支付交易表
CREATE TABLE `payment_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `out_trade_no` varchar(64) NOT NULL COMMENT '外部流水号',
  `amount` decimal(10,2) NOT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0处理中 1成功 2失败',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 业主收入表
CREATE TABLE `owner_earning` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL COMMENT '业主实际收入（扣除平台抽成）',
  `status` tinyint DEFAULT '0' COMMENT '0待结算 1已结算',
  `settle_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_owner_settle` (`owner_id`,`settle_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 3. 启动所有服务
在 docker-compose.yml 所在目录执行：

```bash
bash
docker-compose up -d
```

等待镜像拉取和容器启动完成（首次启动可能需要几分钟）。

## 二、服务访问地址
| 服务              | 地址                              | 用户名/密码               |
| ----------------- | --------------------------------- | ------------------------- |
| MySQL             | `localhost:3306`                  | `root` / `123456`         |
| Redis             | `localhost:6379`                  | 无需密码                  |
| RabbitMQ 管理     | `http://localhost:15672`          | `guest` / `guest`         |
| Elasticsearch     | `http://localhost:9200`           | 无需认证（单节点）        |

## 三、注意事项
- 端口冲突：请确保本地 3306、6379、5672、15672、9200 端口未被占用。

- 内存资源：Elasticsearch 至少需要 512MB 内存，建议给 Docker 分配 2GB+。

- 生产环境：请修改 MySQL 的 MYSQL_ROOT_PASSWORD，启用 RabbitMQ 和 Elasticsearch 的安全认证。

- 建表字符集：默认使用 utf8mb4_0900_ai_ci，若 MySQL 8.0 之前版本需调整。

---

## 贡献与许可
本项目仅供开发测试使用，生产环境请根据需求调整配置。遵循 MIT 协议。
