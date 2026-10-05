# Hệ thống quản lý bảo trì thiết bị

Ứng dụng quản lý tiếp nhận, phân công và xử lý yêu cầu bảo trì. Backend được tách thành các microservice Spring Boot/Java 17 theo nghiệp vụ; frontend dùng Angular.

## Kiến trúc

| Thành phần             | Cổng nội bộ | Trách nhiệm                                               | Database          |
| ---------------------- | ----------: | --------------------------------------------------------- | ----------------- |
| `api-gateway`          |        8080 | Điểm vào API, xác thực JWT, CORS và correlation ID        | Không             |
| `identity-service`     |        8081 | Đăng nhập, user, role, service client/token               | `identity_db`     |
| `asset-service`        |        8082 | Loại thiết bị, thiết bị và tài liệu thiết bị              | `asset_db`        |
| `maintenance-service`  |        8083 | Ticket, SLA, phân công, work log, attachment và dashboard | `maintenance_db`  |
| `inventory-service`    |        8084 | Linh kiện, tồn kho và linh kiện dùng cho ticket           | `inventory_db`    |
| `notification-service` |        8085 | Notification                                              | `notification_db` |
| `organization-service` |        8086 | Khách hàng và địa điểm lắp đặt                             | `organization_db` |

Mỗi service chỉ lưu aggregate thuộc nghiệp vụ của nó. Tham chiếu sang service khác là UUID, không phải quan hệ JPA hay bảng projection. Dữ liệu hiển thị được ghép bằng bulk internal API sau khi đọc dữ liệu local.

```text
identity ───────► organization
asset ──────────► organization
maintenance ────► identity + asset + organization
inventory ──────► maintenance + identity
notification ───► identity
```

HTTP dùng cho validate, tra cứu và xử lý đồng bộ. RabbitMQ chỉ chuyển các sự kiện sau commit cần retry:

- `inventory.part.used` → maintenance cập nhật chi phí và timeline idempotently.
- `inventory.part.low-stock` → notification tạo cảnh báo.
- `maintenance.ticket.changed` → notification; asset chỉ cập nhật trạng thái bảo trì thiết bị.

Các sự kiện projection cũ như `identity.*` và `asset.equipment.changed` không còn được phát hoặc tiêu thụ.

## Mô hình khách hàng và phân quyền

- Dữ liệu vận hành theo cây `Khách hàng → Địa điểm → Thiết bị đã lắp đặt`; mã thiết bị vẫn duy nhất toàn hệ thống.
- Tài khoản `INTERNAL` không gắn khách hàng. `ADMIN` quản trị toàn bộ; `MANAGER` quản lý khách hàng, địa điểm, thiết bị, tài khoản khách hàng và ticket; `TECHNICIAN` chỉ xử lý ticket được phân công.
- Tài khoản `CUSTOMER` có `customerId` và phạm vi `ALL_SITES` hoặc `SELECTED_SITES`. Backend áp dụng phạm vi này cho danh sách, chi tiết, attachment và presigned URL.
- JWT người dùng chứa `account_type`, `customer_id`, `site_scope` và `site_ids`.
- Ticket chụp `warrantyStatusAtCreation` và `warrantyEndAtCreation` khi tạo. Chi phí là dữ liệu vận hành nội bộ và không được trả cho tài khoản khách hàng.
- API công khai dùng `/api/v1/customers` và `/api/v1/customer-sites`; các đường dẫn branch/department cũ không còn được gateway công bố.

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

Web chạy tại `http://localhost:4200`, gateway tại `http://localhost:8080`, RabbitMQ Management tại `http://localhost:15672` và MinIO Console tại `http://localhost:9001`. MinIO API được bind vào `127.0.0.1:9000` để các presigned URL xem trước hoạt động trên máy local; cả hai bucket vẫn là private.

### Object storage MinIO / S3

Compose tạo hai bucket `maintenance-attachments` và `asset-documents`, mỗi service dùng một tài khoản chỉ có quyền trên bucket của mình. Thay toàn bộ mật khẩu `change-*` trong `.env` trước khi triển khai.

Khi chạy trên AWS S3 hoặc nhà cung cấp tương thích, cấu hình cho từng service bằng các biến `S3_ENDPOINT` (để trống với AWS), `S3_PUBLIC_ENDPOINT`, `S3_REGION`, `S3_PATH_STYLE`, `S3_BUCKET`, `S3_ACCESS_KEY` và `S3_SECRET_KEY`. Nếu bỏ access/secret key, SDK dùng default credential chain để nhận IAM role của workload.

Di chuyển attachment từ volume cũ theo hai bước, trong thời gian không có upload mới:

```powershell
# Kiểm tra file, storage_key và SHA-256, không ghi object
docker compose run --rm `
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none `
  -e STORAGE_MIGRATION_ENABLED=true `
  -e STORAGE_MIGRATION_DRY_RUN=true `
  maintenance-service

# Upload idempotent và kiểm tra lại object
docker compose run --rm `
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none `
  -e STORAGE_MIGRATION_ENABLED=true `
  -e STORAGE_MIGRATION_DRY_RUN=false `
  maintenance-service
```

Runner giữ nguyên `storage_key`, bỏ qua object đã có đúng kích thước/checksum và trả lỗi nếu thiếu file hoặc sai hash. Volume `attachments` cũ được mount read-only tại `/legacy-uploads`; không xóa volume trước khi báo cáo migration có `failed=0`.

### Telegram cho kỹ thuật viên

Để bật thông báo Telegram, tạo token mới cho bot rồi cấu hình trong `.env`:

```dotenv
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=<new-token-from-botfather>
TELEGRAM_BOT_USERNAME=technician001_bot
TELEGRAM_APP_PUBLIC_URL=http://localhost:4200
```

Không sử dụng lại token đã từng xuất hiện trong mã nguồn, log hoặc hội thoại. Với môi trường triển khai thật, `TELEGRAM_APP_PUBLIC_URL` phải là URL mà thiết bị của kỹ thuật viên truy cập được. `notification-service` dùng long polling nên chỉ chạy một poller cho mỗi bot token.

Khi được phân công phiếu, kỹ thuật viên có thể bấm **Tiếp nhận phiếu** ngay trong Telegram để chuyển phiếu sang `IN_PROGRESS`; nút **Mở phiếu** vẫn được hiển thị khi `TELEGRAM_APP_PUBLIC_URL` là URL HTTP(S) công khai. Callback chỉ có hiệu lực với đúng tài khoản Telegram và đúng tin nhắn đã nhận.

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

# Smoke test upload → presigned GET 120 giây → private bucket → cleanup qua MinIO local
$env:S3_INTEGRATION_TEST = 'true'
.\mvnw.cmd -s .\.mvn\settings-local.xml -pl services/maintenance-service `
  -Dtest=S3ObjectStorageIntegrationTest test
Remove-Item Env:S3_INTEGRATION_TEST
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
4. Chạy Flyway đồng bộ trên các database; migration đổi tên bảng/cột giữ nguyên UUID.
5. Chạy `infra/postgres/migration/migrate-from-monolith.sql`; script chỉ ghi dữ liệu vào database sở hữu nghiệp vụ, không dựng projection.
6. Import topology RabbitMQ mới và chỉ xóa queue projection cũ sau khi xác nhận rỗng.
7. Khởi động identity với runner tắt, sau đó organization. Khi organization đã healthy, chạy runner identity một lần với `CUSTOMER_SCOPE_MIGRATION_ENABLED=true`; runner dừng lỗi nếu còn user khách hàng không ánh xạ được.
8. Đối chiếu row count và orphan cho customer/site/equipment/ticket/user scope, sau đó khởi động asset → maintenance → inventory → notification → gateway/web.
9. Smoke test login, user, customer/site, equipment, ticket, attachment, part, dashboard và notification; kiểm tra trực tiếp Customer A không đọc được UUID hoặc presigned URL của Customer B.

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

Nếu nguồn có attachment, đặt file cũ vào volume `attachments` và chạy migration runner MinIO/S3 ở trên; runner giữ nguyên `storage_key` và đối chiếu SHA-256 với metadata. Audit legacy chỉ tồn tại trong archive, không được import lại vào database runtime.

Chạy migration phạm vi tài khoản khi stack đã có `organization-service` healthy và đang dừng ghi nghiệp vụ:

```powershell
docker compose run --rm --no-deps `
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none `
  -e CUSTOMER_SCOPE_MIGRATION_ENABLED=true `
  identity-service
```

Lệnh có thể chạy lại an toàn. Chỉ tiếp tục cutover khi log báo `failed=0`; sau đó trả `CUSTOMER_SCOPE_MIGRATION_ENABLED=false`. Với user chỉ có role `REQUESTER`, runner tra địa điểm legacy để gắn customer/site; user có `ADMIN`, `MANAGER` hoặc `TECHNICIAN` được giữ là tài khoản nội bộ và xóa scope tổ chức cũ.

Rollback bằng cách dừng stack mới, phục hồi database dump/RabbitMQ definitions rồi chạy image và Compose cũ.

## Quy ước vận hành

- Chỉ gateway, web, RabbitMQ Management và các cổng observability tùy chọn được publish công khai; MinIO API/Console chỉ bind loopback cho môi trường local.
- PostgreSQL, Redis, AMQP và backend service chỉ nằm trên network nội bộ.
- Mỗi service dùng `ddl-auto=validate`; thay đổi schema phải thêm Flyway migration mới, không sửa migration đã triển khai.
- Access token sống 15 phút; refresh token sống 7 ngày và được rotation.
- Redis giữ rate limit/token denylist; RabbitMQ dùng publisher confirm, retry và DLQ.
- Không commit secret thật; `.env` đã được ignore.
