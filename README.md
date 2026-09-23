# Hệ thống quản lý bảo trì thiết bị

Ứng dụng quản lý tiếp nhận, phân công và xử lý yêu cầu bảo trì. Backend được tách thành các microservice Spring Boot/Java 17 theo nghiệp vụ; frontend dùng Angular.

## Kiến trúc

| Thành phần             | Cổng nội bộ | Trách nhiệm                                               | Database          |
| ---------------------- | ----------: | --------------------------------------------------------- | ----------------- |
| `api-gateway`          |        8080 | Điểm vào API, xác thực JWT, CORS và correlation ID        | Không             |
| `identity-service`     |        8081 | Đăng nhập, user, role, service client/token               | `identity_db`     |
| `asset-service`        |        8082 | Loại thiết bị và thiết bị                                 | `asset_db`        |
| `maintenance-service`  |        8083 | Ticket, SLA, phân công, work log, attachment và dashboard | `maintenance_db`  |
| `inventory-service`    |        8084 | Linh kiện, tồn kho và linh kiện dùng cho ticket           | `inventory_db`    |
| `notification-service` |        8085 | Notification                                              | `notification_db` |
| `organization-service` |        8086 | Phòng ban                                                 | `organization_db` |

Mỗi service chỉ lưu aggregate thuộc nghiệp vụ của nó. Tham chiếu sang service khác là UUID, không phải quan hệ JPA hay bảng projection. Dữ liệu hiển thị được ghép bằng bulk internal API sau khi đọc dữ liệu local.

```text
identity ───────► organization
asset ──────────► organization
maintenance ────► identity + asset
inventory ──────► maintenance + identity
notification ───► identity
```

HTTP dùng cho validate, tra cứu và xử lý đồng bộ. RabbitMQ chỉ chuyển các sự kiện sau commit cần retry:

- `inventory.part.used` → maintenance cập nhật chi phí và timeline idempotently.
- `inventory.part.low-stock` → notification tạo cảnh báo.
- `maintenance.ticket.changed` → notification; asset chỉ cập nhật trạng thái bảo trì thiết bị.

Các sự kiện projection cũ như `identity.*` và `asset.equipment.changed` không còn được phát hoặc tiêu thụ.

## Cấu trúc repository

- `services/`: gateway và sáu Spring Boot service độc lập.
- `contracts/events/`: contract cho các domain event còn được hỗ trợ.
- `infra/postgres/`: khởi tạo database/user và công cụ cutover dữ liệu.
- `infra/rabbitmq/`: exchange, queue, binding và DLQ.
- `infra/prometheus/`, `infra/grafana/`: observability tùy chọn.
- `angular/first-angular-app/`: Angular SPA được phục vụ bởi Nginx.

Source monolith cũ đã được loại khỏi runtime và repository.

## Chạy bằng Docker Compose

Yêu cầu Docker Desktop với Docker Compose.

```powershell
Copy-Item .env.example .env
# Thay toàn bộ giá trị change-* trước khi dùng ngoài máy dev.
docker compose up -d --build --wait
docker compose ps
```

Web chạy tại `http://localhost:4200`, gateway tại `http://localhost:8080`, RabbitMQ Management tại `http://localhost:15672`.

### Telegram cho kỹ thuật viên

Để bật thông báo Telegram, tạo token mới cho bot rồi cấu hình trong `.env`:

```dotenv
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=<new-token-from-botfather>
TELEGRAM_BOT_USERNAME=technician001_bot
TELEGRAM_APP_PUBLIC_URL=http://localhost:4200
```

Không sử dụng lại token đã từng xuất hiện trong mã nguồn, log hoặc hội thoại. Với môi trường triển khai thật, `TELEGRAM_APP_PUBLIC_URL` phải là URL mà thiết bị của kỹ thuật viên truy cập được. `notification-service` dùng long polling nên chỉ chạy một poller cho mỗi bot token.

Trên database trống, tài khoản quản trị lấy từ `BOOTSTRAP_ADMIN_*` trong `.env`. Khi cutover dữ liệu, user và password hash cũ được giữ nguyên.

Dừng stack nhưng giữ dữ liệu:

```powershell
docker compose down
```

`docker compose down -v` xóa toàn bộ database, message và file upload của stack, chỉ dùng khi chủ động reset môi trường.

Observability là profile riêng:

```powershell
docker compose --profile observability up -d --build --wait
```

## Build và kiểm thử

```powershell
.\mvnw.cmd -s .\.mvn\settings-local.xml test
docker compose --env-file .env.example config --quiet
```

Frontend:

```powershell
Set-Location .\angular\first-angular-app
npm ci
npm run build
npm test
```

## Xác thực nội bộ

- `/api/**` chỉ nhận user JWT.
- `/internal/**` nhận service JWT có `scope=internal` và `aud` đúng service đích.
- Service JWT được cấp qua `POST /internal/v1/auth/token` theo `client_credentials`, sống 5 phút.
- Request gắn với người dùng chuyển tiếp user JWT; scheduler/consumer dùng service JWT.
- `X-Correlation-Id` được chuyển tiếp xuyên suốt request.
- HTTP client có connect timeout 2 giây, read timeout 3 giây; chỉ GET/idempotent request được retry tối đa hai lần.

## Cutover dữ liệu

Không chạy monolith và microservices cùng ghi một aggregate. Quy trình chuẩn:

1. Drain outbox/queue và dừng mọi writer.
2. Backup tất cả database bằng `pg_dump -Fc`, xuất RabbitMQ definitions và xác minh dump bằng `pg_restore --list`.
3. Xuất `audit_logs` thành schema + `CSV.gz`, ghi manifest SHA-256 và đối chiếu row count.
4. Khởi tạo sáu database bằng Flyway.
5. Chạy `infra/postgres/migration/migrate-from-monolith.sql`; script chỉ ghi dữ liệu vào database sở hữu nghiệp vụ, không dựng projection.
6. Import topology RabbitMQ mới và chỉ xóa queue projection cũ sau khi xác nhận rỗng.
7. Khởi động theo thứ tự identity → organization → asset → maintenance → inventory → notification → gateway/web.
8. Smoke test login, user, department, equipment, ticket, part, dashboard và notification.

Ví dụ chạy import một lần:

```powershell
$env:SOURCE_DB_PASSWORD = '<source-password>'
docker compose exec -T postgres psql -U postgres -d postgres `
  -v source_host=host.docker.internal `
  -v source_port=5432 `
  -v source_database=quanlybaotri `
  -v source_user=postgres `
  -v source_password=$env:SOURCE_DB_PASSWORD `
  -v source_ticket_sequence=29 `
  -f /migration/migrate-from-monolith.sql
```

Nếu nguồn có attachment, copy file trong `FILE_STORAGE_ROOT` đồng bộ với metadata và giữ nguyên `storage_key`. Audit legacy chỉ tồn tại trong archive, không được import lại vào database runtime.

Rollback bằng cách dừng stack mới, phục hồi database dump/RabbitMQ definitions rồi chạy image và Compose cũ.

## Quy ước vận hành

- Chỉ gateway, web, RabbitMQ Management và các cổng observability tùy chọn được publish ra host.
- PostgreSQL, Redis, AMQP và backend service chỉ nằm trên network nội bộ.
- Mỗi service dùng `ddl-auto=validate`; thay đổi schema phải thêm Flyway migration mới, không sửa migration đã triển khai.
- Access token sống 15 phút; refresh token sống 7 ngày và được rotation.
- Redis giữ rate limit/token denylist; RabbitMQ dùng publisher confirm, retry và DLQ.
- Không commit secret thật; `.env` đã được ignore.
