package com.therapyCommunity_Vol1.backend;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class BackendApplicationTests {

	@MockitoBean
	private FileStorageService fileStorageService;

	@Test
	void contextLoads() {
	}

}
