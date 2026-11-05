# Vai trò và phạm vi truy cập

## Người yêu cầu
REQUESTER thuộc tài khoản khách hàng được tạo và theo dõi phiếu cho thiết bị thuộc khách hàng và địa điểm đã được cấp quyền. Người yêu cầu không thể xem dữ liệu của khách hàng hoặc địa điểm khác.

## Kỹ thuật viên
TECHNICIAN xem các phiếu được phân công, cập nhật tiến độ, nhật ký, linh kiện và chi phí theo trạng thái cho phép. Kỹ thuật viên không tạo phiếu thay khách hàng.

## Quản lý và quản trị
MANAGER và ADMIN có thể tiếp nhận, từ chối, phân công, đổi ưu tiên và tạo phiếu thay khách hàng. Khi tạo thay phải chọn một requester đang hoạt động có quyền tại địa điểm của thiết bị.

## Bảo mật dữ liệu
Mọi tra cứu đều áp dụng quyền từ JWT và phạm vi khách hàng, địa điểm hoặc phiếu được phân công. Trợ lý AI không thể mở rộng quyền của tài khoản đang đăng nhập.
