# Gateway-API
该模块为网关系统中的后端API

## 更新依赖命令
```bash
mvn dependency:purge-local-repository
```

## 部署与运行
### 构建镜像
```bash
docker build --platform=linux/arm64 --build-arg username=${GITHUB_USERNAME} --build-arg password=${GITHUB_TOKEN} --force-rm -t slenergy/gateway-api:1.0 .
```

### 运行容器命令
```bash
# 创建网络
docker network create --subnet=10.172.43.0/24 --gateway=10.172.43.1 slenergy-gateway-network
# 运行容器
docker run --name api \
--network slenergy-gateway-network --ip 10.172.43.5 -p 8099:8099 \
--device=/dev/ttyUSB0:/dev/ttyUSB0 --device=/dev/ttymxc2:/dev/ttyUSB1 \
-v /proc/device-tree/serial-number:/serial-number:ro -v /var/run/docker.sock:/var/run/docker.sock:ro \
-v /slenergy-gateway/config:/data/config:ro -v /slenergy-gateway/sqlite:/data/sqlite:rw \
-d slenergy/gateway-api:1.0
```

#### 运行容器必要参数
- 网络
- 串口映射
  - 具体串口需要针对个人情况自行修改
- 板子序列号与docker文件映射
- ip地址目录的映射，与获取ip地址的容器映射同一个目录
  - 该目录地址需要针对个人情况自行修改