package org.transportsgt.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Secreto fijo de pruebas: en el build de Docker no existe .env ni JWT_SECRET
@SpringBootTest(properties = "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

}
