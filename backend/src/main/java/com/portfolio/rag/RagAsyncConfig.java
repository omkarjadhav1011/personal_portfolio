package com.portfolio.rag;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async} so RAG re-indexing runs off the request thread (Phase D1). */
@Configuration
@EnableAsync
public class RagAsyncConfig {
}
