# Hệ thống quản lý bảo trì thiết bị

Ứng dụng quản lý tiếp nhận, phân công và xử lý yêu cầu bảo trì. Backend được tách thành các microservice Spring Boot/Java 17 theo nghiệp vụ; frontend dùng Angular.

## Kiến trúc

| Thành phần             | Cổng nội bộ | Trách nhiệm                                               | Database          |
| ---------------------- | ----------: | --------------------------------------------------------- | ----------------- |
| `api-gateway`          |        8080 | Điểm vào API, xác thực JWT, CORS và correlation ID        | Không             |
| `identity-service`     |        8081 | Đăng nhập, user, role, service client/token               | `identity_db`     |
| `asset-service`        |        8082 | Loại thiết bị, thiết bị và tài liệu thiết bị              | `asset_db`        |
| `maintenance-service`  |        8083 | Ticket, SLA, chi phí, attachment và dashboard             | `maintenance_db`  |
| `inventory-service`    |        8084 | Linh kiện, tồn kho và linh kiện dùng cho ticket           | `inventory_db`    |
| `notification-service` |        8085 | Notification                                              | `notification_db` |
| `organization-service` |        8086 | Khách hàng và địa điểm lắp đặt                             | `organization_db` |
| `payment-service`      |        8087 | Thanh toán, VNPay, đối soát và lịch sử giao dịch           | `payment_db`      |
| `ai-service`           |        8088 | Trợ lý NVIDIA Nemotron, tra cứu và tạo bản nháp phiếu        | Redis (tạm thời)  |

Mỗi service chỉ lưu aggregate thuộc nghiệp vụ của nó. Tham chiếu sang service khác là UUID, không phải quan hệ JPA hay bảng projection. Dữ liệu hiển thị được ghép bằng bulk internal API sau khi đọc dữ liệu local.

```text
identity ───────► organization
asset ──────────► organization
maintenance ────► identity + asset + organization
payment ────────► maintenance + organization
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
- Ticket chụp `warrantyStatusAtCreation` và `warrantyEndAtCreation` khi tạo. Khách hàng chỉ thấy bảng chi phí đã duyệt theo nhóm; từng dòng chi phí, đơn giá và chứng từ nội bộ không được trả ra.
- API công khai dùng `/api/v1/customers` và `/api/v1/customer-sites`; các đường dẫn branch/department cũ không còn được gateway công bố.

## Cấu trúc repository

- `services/`: gateway và bảy Spring Boot service độc lập.
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

### Thanh toán VNPay

`payment-service` sở hữu bảng `payment_attempts` trong `payment_db`. Service lấy ticket/chi phí qua internal API của `maintenance-service`; ngược lại maintenance chỉ gọi payment contract để kiểm tra điều kiện đóng/mở lại phiếu và ghép payment snapshot. Gateway giữ nguyên các URL public `/api/v1/payments/**` và `/api/v1/tickets/{id}/payments`.

Với volume PostgreSQL đã tồn tại, init script không chạy lại. Tạo database/user payment một lần trước khi bật service mới:

```powershell
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" --set=payment_password="$PAYMENT_DB_PASSWORD" --file=/migration/04-create-payment-database.sql'
docker compose up -d --build payment-service api-gateway
```

Khách hàng có quyền trên địa điểm của phiếu có thể thanh toán toàn bộ bảng chi phí đã duyệt. Phiếu có tổng tiền lớn hơn 0 chỉ được đóng sau khi IPN hợp lệ xác nhận thành công; bảng chi phí bằng 0 không yêu cầu thanh toán. Cấu hình Sandbox trong `.env`:

```dotenv
VNPAY_TMN_CODE=<sandbox-tmn-code>
VNPAY_HASH_SECRET=<sandbox-hash-secret>
VNPAY_PAYMENT_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_QUERY_URL=https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
VNPAY_RETURN_URL=https://app.example.com/api/v1/payments/vnpay/return
VNPAY_IPN_URL=https://app.example.com/api/v1/payments/vnpay/ipn
VNPAY_FRONTEND_RESULT_URL=https://app.example.com/payment-result
VNPAY_PAYMENT_EXPIRY=15m
```

`VNPAY_IPN_URL` phải là HTTPS URL công khai được đăng ký với VNPay. Hai callback VNPay là các endpoint công khai duy nhất trong payment API và đều kiểm tra HMAC-SHA512; secret không được ghi vào source, log hoặc frontend. ADMIN/MANAGER có thể đối soát giao dịch đang chờ từ trang chi tiết phiếu sau khoảng chờ tối thiểu 5 phút.

### Telegram cho kỹ thuật viên

Để bật thông báo Telegram, tạo token mới cho bot rồi cấu hình trong `.env`:

```dotenv
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=<new-token-from-botfather>
TELEGRAM_BOT_USERNAME=technician001_bot
TELEGRAM_APP_PUBLIC_URL=http://localhost:4200
TELEGRAM_CONVERSATION_TTL=2h
```

Không sử dụng lại token đã từng xuất hiện trong mã nguồn, log hoặc hội thoại. Với môi trường triển khai thật, `TELEGRAM_APP_PUBLIC_URL` phải là URL mà thiết bị của kỹ thuật viên truy cập được. `notification-service` dùng long polling nên chỉ chạy một poller cho mỗi bot token.

Khi được phân công phiếu, kỹ thuật viên có thể bấm **Tiếp nhận phiếu** ngay trong Telegram để chuyển phiếu sang `IN_PROGRESS`; nút **Mở phiếu** vẫn được hiển thị khi `TELEGRAM_APP_PUBLIC_URL` là URL HTTP(S) công khai. Callback chỉ có hiệu lực với đúng tài khoản Telegram và đúng tin nhắn đã nhận.

Bot hỗ trợ xử lý trọn phần việc của kỹ thuật viên trong private chat:

- `/phieu`: xem và chuyển giữa các phiếu đang được giao.
- `/huy`: hủy bước nhập hiện tại và quay về menu phiếu.
- `/trogiup`: xem hướng dẫn nhanh.
- Menu phiếu cho phép thêm nhật ký/thời gian, ảnh hoặc chứng từ, xuất linh kiện, khai báo khoản phát sinh có mô tả, chờ/tiếp tục, hoàn tất xử lý và gửi báo giá cho khách hàng. Thời gian làm việc không được tính vào chi phí phiếu.

Phiên nhập hết hạn sau `TELEGRAM_CONVERSATION_TTL` không hoạt động. Ảnh tối đa 10 MB; video và tài liệu gửi qua bot tối đa 20 MB. Bot không nhận voice note, sticker hoặc vị trí. Kỹ thuật viên đưa phiếu tới `RESOLVED` và báo giá tới `QUOTED`; người tạo phiếu chấp nhận hoặc từ chối báo giá trước khi thanh toán và đóng phiếu.

Khi triển khai nâng cấp, chạy Flyway cho `maintenance-service`, `inventory-service` và `notification-service` trước khi bật bot. Các callback **Tiếp nhận phiếu** cũ vẫn tương thích sau nâng cấp.

Trên database trống, tài khoản quản trị lấy từ `BOOTSTRAP_ADMIN_*` trong `.env`. Khi cutover dữ liệu, user và password hash cũ được giữ nguyên.

### Google Sign-In

Tạo OAuth 2.0 Client ID loại **Web application** trong Google Cloud Console, thêm các địa chỉ web thực tế (ví dụ `http://localhost:4200`) vào **Authorized JavaScript origins**, rồi đặt Client ID vào `GOOGLE_CLIENT_ID` trong `.env`. Luồng Google Identity Services dùng popup và ID token nên không cần client secret hoặc redirect URI. Để áp dụng migration tài khoản và cấu hình mới, rebuild `identity-service`, `api-gateway` và `web`:

```powershell
docker compose up -d --build identity-service api-gateway web
```

Nếu `GOOGLE_CLIENT_ID` để trống, backend trả `googleEnabled=false` và giao diện tự ẩn nút Google.

Dừng stack nhưng giữ dữ liệu:

```powershell
docker compose down
```

`docker compose down -v` xóa toàn bộ database, message và file upload của stack, chỉ dùng khi chủ động reset môi trường.

Observability là profile riêng:

```powershell
docker compose --profile observability up -d --build --wait
```

### Chat realtime theo phiếu

`notification-service` sở hữu dữ liệu chat và cung cấp REST đọc hộp thư/lịch sử tại
`/api/v1/chat/**`. Gửi tin và đánh dấu đã đọc dùng STOMP qua WebSocket cùng origin
`/ws/chat`; token truy cập được gửi trong header `Authorization` của STOMP `CONNECT`.

RabbitMQ chạy cả AMQP và plugin STOMP. Cổng `5672` và `61613` chỉ nằm trong mạng
`backend`; trình duyệt luôn kết nối qua Nginx, không nhận RabbitMQ credential. Khi triển
khai trên domain khác localhost, đặt chính xác origin được phép, ví dụ:

```dotenv
CHAT_ALLOWED_ORIGIN_PATTERNS=https://maintenance.example.com
```

Sau khi cập nhật source hoặc biến môi trường, rebuild các thành phần liên quan:

```powershell
docker compose up -d --build rabbitmq notification-service api-gateway web
```

Phòng chat chỉ cho requester đã tạo phiếu, kỹ thuật viên đang được phân công,
`ADMIN` và `MANAGER`. Phiếu `CLOSED`, `REJECTED`, `CANCELLED` chỉ được đọc. Trang
`/messages` là hộp thư; cùng component chat cũng xuất hiện trong chi tiết phiếu.

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

## Trợ lý AI NVIDIA

`ai-service` gọi model NVIDIA Nemotron qua OpenRouter với model mặc định `nvidia/nemotron-3.5-super-vl`. Tạo API key trên OpenRouter rồi đặt `OPENROUTER_API_KEY` trong file `.env`; không commit khóa thật vào Git.

```powershell
docker compose up -d --build ai-service api-gateway web
docker compose ps
```

Reasoning mặc định bị tắt, intent tối đa 256 output token, tư vấn quy trình tối đa 400 token và chat tổng quát tối đa 800 token. Trợ lý vẫn ưu tiên các luồng bảo trì có kiểm soát, đồng thời trả lời như chat thông thường cho câu hỏi ngoài nghiệp vụ. Có thể đổi endpoint, model, timeout, TTL phiên, giới hạn kết quả và rate limit bằng các biến trong `.env.example`. Hội thoại chỉ tồn tại tạm trong Redis; JWT không được gửi tới nhà cung cấp AI và nội dung chat không được lưu vào database của ứng dụng.

Prompt và output được gửi qua OpenRouter tới nhà cung cấp model. Không gửi dữ liệu cá nhân hoặc bí mật nếu tổ chức chưa phê duyệt việc xử lý dữ liệu bên ngoài hệ thống.
