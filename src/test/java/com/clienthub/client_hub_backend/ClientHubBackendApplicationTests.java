package com.clienthub.client_hub_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, 
				properties = {
					"spring.datasource.url=jdbc:h2:mem:testdb",
					"spring.datasource.driver-class-name=org.h2.Driver",
					"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
				})
class ClientHubBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
