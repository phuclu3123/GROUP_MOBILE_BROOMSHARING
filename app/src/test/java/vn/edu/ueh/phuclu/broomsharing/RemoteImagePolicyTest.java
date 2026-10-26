package vn.edu.ueh.phuclu.broomsharing;

import static org.junit.Assert.*;
import org.junit.Test;
import vn.edu.ueh.phuclu.broomsharing.repository.RemoteImageClient;

// Kiểm URL trước khi tạo yêu cầu mạng; không gọi DNS cho tên miền trong các ca này.
public class RemoteImagePolicyTest {
    @Test public void directHttpsImageAccepted() {
        assertEquals("images.unsplash.com",RemoteImageClient.imageUrl("https://images.unsplash.com/example.jpg?w=800").host());
    }
    @Test public void unsafeUrlsRejected() {
        for (String url : new String[]{"http://example.com/a.jpg","https://user:pass@example.com/a.jpg",
                "https://localhost/a.jpg","https://127.0.0.1/a.jpg","https://192.168.1.1/a.jpg",
                "https://169.254.169.254/a.jpg","https://100.64.0.1/a.jpg","https://[::1]/a.jpg",
                "https://example.com:8443/a.jpg","https://example.com/a.jpg#fragment","file:///etc/file"}) {
            try { RemoteImageClient.imageUrl(url); fail("Accepted unsafe URL: "+url); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
