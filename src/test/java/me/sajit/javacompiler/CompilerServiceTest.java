package me.sajit.javacompiler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CompilerServiceTest {

    @Autowired
    private CompilerService compilerService;

    @Test
    public void testHelloWorld() {
        String javaCode = """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
            """;

        ResponseEntity<Map<String, Object>> response = compilerService.compile(javaCode, "");
        assertThat(response.getBody().get("status")).isEqualTo("success");
        assertThat(response.getBody().get("message").toString()).contains("Hello, World!");
    }

    @Test
    public void testHttpRequestNotAllowed() {
        String javaCode = """
            import java.net.HttpURLConnection;
            import java.net.URL;

            public class HttpExample {
                public static void main(String[] args) throws Exception {
                    URL url = new URL("http://example.com");
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");
                }
            }
            """;

        ResponseEntity<Map<String, Object>> response = compilerService.compile(javaCode, "");
        assertThat(response.getBody().get("status")).isEqualTo("error");
        assertThat(response.getBody().get("errors").toString()).contains("HTTP requests are not allowed in the code.");
    }
} 