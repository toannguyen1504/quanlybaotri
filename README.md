# Hệ thống quản lý bảo trì thiết bị

Ứng dụng nội bộ quản lý tiếp nhận, phân công và xử lý yêu cầu bảo trì. Backend là modular monolith Spring Boot 4.1.1/Java 17; frontend dùng Angular 22; dữ liệu lưu trên PostgreSQL, Redis và RabbitMQ hỗ trợ cache, token denylist, rate limit và xử lý sự kiện bất đồng bộ.

## Cấu trúc

- `quanlybaotri/quanlybaotri`: REST API Spring Boot.
- `angular/first-angular-app`: Angular SPA.
- `quanlybaotri/quanlybaotri/data/uploads`: thư mục file mặc định ở môi trường dev.

Backend được chia theo feature: `identity`, `organization`, `equipment`, `ticket`, `inventory`, `notification`, `reporting`, `shared`. Mỗi thay đổi quan trọng của phiếu ghi `ticket_events` và `outbox_events` trong cùng transaction PostgreSQL.

## Yêu cầu cài đặt

- JDK 17 và `JAVA_HOME` trỏ đến JDK 17.
- Node.js và npm (Angular 22).
- PostgreSQL và RabbitMQ đang chạy cục bộ.
- Redis chạy trong Ubuntu trên WSL 2 và được chuyển tiếp tới `localhost:6379` của Windows.
- Database dev tên `quanlybaotri`, tài khoản mặc định `postgres/123456`.

Tạo database nếu chưa có:

```powershell
psql -U postgres -c "CREATE DATABASE quanlybaotri;"
```

Flyway tự tạo toàn bộ bảng và dữ liệu vai trò/SLA khi backend khởi động. Không dùng `ddl-auto=update`; Hibernate chỉ `validate` schema.

### Cài Redis lần đầu trên Windows

Mở PowerShell bằng quyền Administrator và cài Ubuntu cho WSL 2:

```powershell
wsl.exe --install -d Ubuntu
```

Khởi động lại Windows nếu được yêu cầu, mở Ubuntu và hoàn tất việc tạo tài khoản Linux. Sau đó cài Redis từ kho APT chính thức:

```bash
sudo apt-get install -y lsb-release curl gpg
curl -fsSL https://packages.redis.io/gpg | sudo gpg --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg
sudo chmod 644 /usr/share/keyrings/redis-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/redis-archive-keyring.gpg] https://packages.redis.io/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/redis.list
sudo apt-get update
sudo apt-get install -y redis
sudo systemctl enable redis-server
```

Nếu `systemctl` báo WSL không chạy systemd, thêm cấu hình sau vào `/etc/wsl.conf`:

```ini
[boot]
systemd=true
```

Sau đó chạy `wsl.exe --shutdown` từ PowerShell, mở lại Ubuntu và thực hiện lại lệnh `sudo systemctl enable redis-server`.

Giữ cấu hình Redis mặc định chỉ bind loopback, bật protected mode và dùng cổng `6379`. Không bind `0.0.0.0` hoặc mở cổng Redis trên firewall cho môi trường dev.

Script quản lý Redis từ thư mục gốc dự án:

```powershell
.\scripts\redis-dev.ps1 start
.\scripts\redis-dev.ps1 status
.\scripts\redis-dev.ps1 stop
```

Lệnh `start` giữ một tiến trình WSL tối thiểu chạy ẩn để Ubuntu không tự dừng service khi không còn terminal Linux nào mở. Lệnh `stop` dừng Redis và tiến trình giữ phiên này.

Nếu Redis trả `PONG` trong WSL nhưng Windows không truy cập được `localhost:6379`, bảo đảm `%UserProfile%\.wslconfig` có:

```ini
[wsl2]
localhostForwarding=true
networkingMode=mirrored
```

Chạy `wsl.exe --shutdown`, rồi chạy lại script với lệnh `start`.

### Cài RabbitMQ lần đầu trên Windows

Môi trường dev hiện dùng gói RabbitMQ `4.0.5` từ kho APT của Ubuntu 26.04. Cách cài này chỉ dành cho máy phát triển vì Ubuntu 26.04 chưa nằm trong danh sách distro được RabbitMQ hỗ trợ chính thức; production phải dùng một distro và RabbitMQ release còn được nhà cung cấp hỗ trợ. Xem [hướng dẫn cài đặt chính thức](https://www.rabbitmq.com/docs/install-debian).

Trong Ubuntu WSL, cài RabbitMQ và giới hạn AMQP/Management ở loopback:

```bash
sudo apt-get update
sudo apt-get install -y rabbitmq-server
sudo install -d -m 0755 /etc/rabbitmq
sudo tee /etc/rabbitmq/rabbitmq.conf >/dev/null <<'EOF'
listeners.tcp.default = 127.0.0.1:5672
management.tcp.ip = 127.0.0.1
management.tcp.port = 15672
EOF
sudo systemctl disable --now rabbitmq-server
```

Sau đó quản lý RabbitMQ từ thư mục gốc dự án:

```powershell
.\scripts\rabbitmq-dev.ps1 start
.\scripts\rabbitmq-dev.ps1 status
.\scripts\rabbitmq-dev.ps1 stop
```

Lệnh `start` bật management plugin và tạo/cập nhật idempotent vhost `quanlybaotri` cùng tài khoản dev `maintenance_app/MaintenanceRabbit@123`. Đây là credential công khai chỉ dùng cho local dev. Management UI ở `http://localhost:15672`. Script giữ một tiến trình WSL riêng, nên có thể start/stop RabbitMQ độc lập với Redis.

## Chạy môi trường phát triển

Khởi động Redis và RabbitMQ trước:

```powershell
.\scripts\redis-dev.ps1 start
.\scripts\rabbitmq-dev.ps1 start
```

Backend:

```powershell
cd .\quanlybaotri\quanlybaotri
.\mvnw.cmd -s .mvn\settings-local.xml spring-boot:run
```

Frontend (terminal khác):

```powershell
cd .\angular\first-angular-app
npm install
npm start
```

Mở `http://localhost:4200`. Tài khoản bootstrap dev mặc định:

- Username: `admin`
- Password: `Admin@123`

Đổi các giá trị này trước khi dùng ngoài máy phát triển. Swagger UI ở `http://localhost:8080/swagger-ui.html`; health check ở `http://localhost:8080/actuator/health`. RabbitMQ Management thường ở `http://localhost:15672` nếu plugin quản trị đã bật.

## Biến môi trường production

Chạy với profile `prod` và khai báo tối thiểu:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:postgresql://localhost:5432/quanlybaotri"
$env:DB_USERNAME="maintenance_app"
$env:DB_PASSWORD="replace-with-strong-password"
$env:JWT_SECRET="replace-with-at-least-32-random-bytes"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:RABBITMQ_HOST="localhost"
$env:RABBITMQ_PORT="5672"
$env:RABBITMQ_USERNAME="maintenance_app"
$env:RABBITMQ_PASSWORD="replace-with-strong-password"
$env:RABBITMQ_VIRTUAL_HOST="quanlybaotri"
$env:RABBITMQ_HEALTH_ENABLED="false"
$env:APP_OUTBOX_CONFIRM_TIMEOUT="5s"
$env:FILE_STORAGE_ROOT="D:\maintenance-data\uploads"
$env:CORS_ALLOWED_ORIGIN="https://maintenance.example.com"
$env:BOOTSTRAP_ADMIN_USERNAME="admin"
$env:BOOTSTRAP_ADMIN_PASSWORD="replace-with-strong-password"
$env:BOOTSTRAP_ADMIN_EMAIL="admin@example.com"
```

Sau đó chạy file JAR:

```powershell
cd .\quanlybaotri\quanlybaotri
.\mvnw.cmd -s .mvn\settings-local.xml clean package
java -jar .\target\quanlybaotri-0.0.1-SNAPSHOT.jar
```

## API chính

Tất cả API dùng prefix `/api/v1`, Bearer JWT, thời gian UTC ISO-8601 và `ProblemDetail` cho lỗi.

- Authentication và hồ sơ cá nhân: `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/me`,
  `/users/me/profile`, `/users/me/password`.
- Quản trị: `/users`, `/roles`, `/departments`, `/sla-policies`.
- Thiết bị: `/equipment-categories`, `/equipment`.
- Phiếu: `/tickets` cùng các command `accept`, `reject`, `assign`, `start`, `wait-parts`, `resume`, `resolve`, `close`, `reopen`, `cancel`, `priority`.
- Chi tiết phiếu: `timeline`, `work-logs`, `attachments`, `parts`; chi phí bắt đầu ở `PENDING`, sau khi sửa xong mới chọn `FREE/PAID`, và số tiền trả phí tự tính từ linh kiện đã dùng.
- Kho: `/parts`, `/stock-movements`.
- Thông báo và báo cáo: `/notifications`, `/reports/dashboard`.

Access token sống 15 phút; refresh token sống 7 ngày và được rotation. Redis giới hạn đăng nhập 5 lần/15 phút theo tài khoản và IP. Nếu Redis lỗi, nghiệp vụ vẫn đọc PostgreSQL; nếu RabbitMQ lỗi, giao dịch chính vẫn hoàn thành và outbox relay gửi lại sau.

## Kiểm thử và build

```powershell
cd .\quanlybaotri\quanlybaotri
.\mvnw.cmd -s .mvn\settings-local.xml test
```

Integration test sử dụng PostgreSQL `quanlybaotri` và rollback dữ liệu nghiệp vụ sau mỗi test. Cần tạo một database test riêng và đặt `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` khi chạy CI hoặc khi không muốn dùng database dev.

Bộ test mặc định không cần RabbitMQ. Để chạy thêm smoke test với broker thật, khởi động RabbitMQ rồi bật cờ riêng:

```powershell
.\scripts\rabbitmq-dev.ps1 start
$env:RABBITMQ_IT="true"
cd .\quanlybaotri\quanlybaotri
.\mvnw.cmd -s .mvn\settings-local.xml test
Remove-Item Env:RABBITMQ_IT
```

Smoke test kiểm tra topology, outbox → consumer → notification, publisher return khi không có route và retry → DLQ. Nên trỏ `TEST_DB_*` đến database test riêng; dữ liệu do smoke test tạo được dọn sau khi chạy.

```powershell
cd .\angular\first-angular-app
npm run build
npm test
```

## Backup và khôi phục

Database và thư mục `FILE_STORAGE_ROOT` phải được backup cùng thời điểm:

```powershell
pg_dump -U maintenance_app -Fc -d quanlybaotri -f .\backup\quanlybaotri.dump
Compress-Archive -Path D:\maintenance-data\uploads -DestinationPath .\backup\uploads.zip
```

Khôi phục database vào database rỗng:

```powershell
pg_restore -U maintenance_app -d quanlybaotri --clean --if-exists .\backup\quanlybaotri.dump
```

Giữ nguyên đường dẫn tương đối trong thư mục upload vì metadata file nằm trong PostgreSQL. Không chỉnh sửa migration `V1__initial_schema.sql` sau khi đã triển khai; mọi thay đổi schema tiếp theo phải tạo migration `V2`, `V3`, ...
