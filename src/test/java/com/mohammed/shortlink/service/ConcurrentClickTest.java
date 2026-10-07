package com.mohammed.shortlink.service;

import com.mohammed.shortlink.entity.ShortLink;
import com.mohammed.shortlink.repository.ShortLinkRepository;
import com.mohammed.shortlink.util.Base62;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentClickTest {
    @Autowired private ShortLinkService service;
    @Autowired private ShortLinkRepository repository;

    @Test
    void parallelRedirectsDoNotLoseClickIncrements() throws Exception {
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com/concurrent", null));
        String code = Base62.encode(link.getId());
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                tasks.add(workers.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    for (int visit = 0; visit < 10; visit++) {
                        assertThat(service.redirectAndRecordClick(code)).isEqualTo("https://example.com/concurrent");
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
            ShortLink clicked = repository.findById(link.getId()).orElseThrow();
            assertThat(clicked.getClickCount()).isEqualTo(80);
            assertThat(clicked.getLastClickedAt()).isNotNull();
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            repository.deleteById(link.getId());
        }
    }
}
