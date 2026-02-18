package com.triagemate.ingest;

import com.triagemate.testsupport.KafkaIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TriagemateIngestApplicationTests extends KafkaIntegrationTestBase {

	@Test
	void contextLoads() {}
}
