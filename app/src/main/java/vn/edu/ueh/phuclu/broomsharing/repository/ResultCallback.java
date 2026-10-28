package vn.edu.ueh.phuclu.broomsharing.repository;



/*
   Hợp đồng trả dữ liệu hoặc lỗi từ Repository về bên gọi.
   Bên gọi quản lý trạng thái tải và vòng đời; callback không đại diện một lần ghi thành công nếu chưa nhận onSuccess.
*/
public interface ResultCallback<T> {
    void onSuccess(T result);

    void onError(Exception error);
}
