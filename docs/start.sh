# Central Auth Platform — 快速启动脚本
# 用法: bash /root/central-auth-platform/docs/start.sh

set -e

echo "=== Central Auth Platform 启动 ==="

# 1. 检查 MySQL
echo "[1/4] 检查 MySQL 连接..."
if mysql -h 10.10.10.12 -P 3306 -u cap_user -p'claudeloveu@@##' central_auth_platform -e "SELECT 1" > /dev/null 2>&1; then
    echo "  ✅ MySQL 连接正常"
else
    echo "  ⚠️  MySQL 连接失败，请检查网络和数据库"
fi

# 2. 编译
echo "[2/4] 编译项目..."
cd /root/central-auth-platform
mvn clean package -DskipTests -q
echo "  ✅ 编译完成"

# 3. 停止旧进程
echo "[3/4] 停止旧进程..."
OLD_PID=$(pgrep -f "cap-server.*jar" || true)
if [ -n "$OLD_PID" ]; then
    kill $OLD_PID 2>/dev/null || true
    sleep 2
    echo "  ✅ 已停止旧进程 PID=$OLD_PID"
else
    echo "  ℹ️  没有正在运行的旧进程"
fi

# 4. 启动
echo "[4/4] 启动后端..."
nohup java -jar /root/central-auth-platform/cap-server/target/cap-server-1.0.0-SNAPSHOT.jar > /tmp/cap-server.log 2>&1 &
NEW_PID=$!
echo "  ✅ 后端已启动 PID=$NEW_PID"
echo "  📋 日志: tail -f /tmp/cap-server.log"

# Nginx
echo ""
echo "检查 Nginx..."
if nginx -t > /dev/null 2>&1; then
    systemctl restart nginx 2>/dev/null || nginx -s reload
    echo "  ✅ Nginx 已重载"
else
    echo "  ⚠️  Nginx 未运行或配置错误"
fi

echo ""
echo "=== 启动完成 ==="
echo "  访问地址: http://$(hostname -I | awk '{print $1}')/"
echo "  管理员:   admin / admin123"
echo "  API 日志: tail -f /tmp/cap-server.log"
