# Quy trình phiếu bảo trì

## Tạo phiếu
Người yêu cầu chọn thiết bị đang hoạt động, nhập tiêu đề, mô tả sự cố và mức ưu tiên. Mức LOW dùng khi không ảnh hưởng trực tiếp; MEDIUM khi ảnh hưởng một phần nhưng còn phương án thay thế; HIGH khi làm gián đoạn công việc; CRITICAL khi dừng hoạt động hoặc ảnh hưởng diện rộng. Phiếu mới có trạng thái SUBMITTED.

## Tiếp nhận và phân công
ADMIN hoặc MANAGER xem xét phiếu SUBMITTED để chấp nhận hoặc từ chối. Phiếu được chấp nhận chuyển sang ACCEPTED, sau đó được phân công cho kỹ thuật viên phù hợp và chuyển sang ASSIGNED.

## Xử lý và chờ linh kiện
Kỹ thuật viên được phân công bắt đầu xử lý để chuyển phiếu sang IN_PROGRESS. Khi thiếu vật tư có thể chuyển sang WAITING_PARTS và tiếp tục lại IN_PROGRESS sau khi có đủ linh kiện.

## Hoàn tất và đóng phiếu
Kỹ thuật viên ghi kết quả và chuyển phiếu sang RESOLVED. Người có quyền kiểm tra kết quả rồi đóng phiếu thành CLOSED. Phiếu RESOLVED có thể được mở lại về IN_PROGRESS nếu sự cố chưa được xử lý đầy đủ.

## SLA
Mỗi mức ưu tiên có thời hạn phản hồi và thời hạn xử lý riêng. Dashboard và danh sách công việc hiển thị phiếu sắp đến hạn hoặc quá hạn; user nên chọn đúng mức ưu tiên theo tác động thực tế.
