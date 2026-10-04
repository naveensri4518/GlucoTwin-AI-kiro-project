package com.glucotwin.infrastructure.scheduling;

import com.glucotwin.application.MarkStaleTwinsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StaleTwinDetectionJob {

    private final MarkStaleTwinsUseCase markStaleTwinsUseCase;

    @Scheduled(fixedDelayString = "${glucotwin.twin.stale-check-interval-ms:300000}")
    public void run() {
        int count = markStaleTwinsUseCase.execute();
        if (count > 0) {
            log.info("StaleTwinDetectionJob: marked {} twins as STALE", count);
        }
    }
}
