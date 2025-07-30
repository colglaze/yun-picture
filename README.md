# 云图片管理系统

## 项目简介
这是一个基于 Spring Boot 的云图片管理系统，支持图片上传、存储和管理功能。

## 环境要求
- JDK 8+
- Maven 3.6+
- MySQL 5.7+

## 快速开始

### 1. 克隆项目
```bash
git clone https://github.com/colglaze/yun-picture.git
cd yun-picture
```

### 2. 配置数据库
- 创建数据库 `yu_picture`
- 修改 `src/main/resources/application.yml` 中的数据库连接信息

### 3. 配置本地开发环境
复制本地配置文件模板并填入真实配置：
```bash
cp src/main/resources/application-local-template.yml src/main/resources/application-local.yml
```

编辑 `application-local.yml` 文件，填入你的配置：
- 数据库连接信息
- 腾讯云COS配置：
  - `secretId`: 腾讯云 Secret ID
  - `secretKey`: 腾讯云 Secret Key
  - `region`: 存储桶所在地域
  - `bucket`: 存储桶名称
  - `host`: COS访问域名

### 4. 运行项目
```bash
mvn spring-boot:run
```

项目将在 `http://localhost:8123/api` 启动

## 环境变量配置
你也可以通过环境变量来配置COS参数：
- `COS_SECRET_ID`: 腾讯云 Secret ID
- `COS_SECRET_KEY`: 腾讯云 Secret Key
- `COS_REGION`: 存储桶所在地域
- `COS_BUCKET`: 存储桶名称
- `COS_HOST`: COS访问域名

## 安全说明
- 请勿将包含真实密钥的配置文件提交到代码仓库
- 生产环境建议使用环境变量或密钥管理服务
- 本地开发配置文件 `application-local.yml` 已被添加到 `.gitignore`

## 技术栈
- Spring Boot
- MyBatis Plus
- MySQL
- 腾讯云COS
- Knife4j (API文档) 