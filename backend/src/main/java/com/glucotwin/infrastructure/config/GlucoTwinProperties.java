package com.glucotwin.infrastructure.config;

import com.glucotwin.domain.prediction.RiskThresholds;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "glucotwin")
@Getter @Setter
public class GlucoTwinProperties {

    private Twin twin = new Twin();
    private Prediction prediction = new Prediction();
    private MlService mlService = new MlService();
    private Security security = new Security();
    private Data data = new Data();
    private Ai ai = new Ai();

    @Getter @Setter
    public static class Twin {
        private long stalenessThresholdMinutes = 30;
        private long staleCheckIntervalMs = 300_000;
        private int cgmHistoryMaxSize = 12;
    }

    @Getter @Setter
    public static class Prediction {
        private RiskThresholdsConfig riskThresholds = new RiskThresholdsConfig();
    }

    @Getter @Setter
    public static class RiskThresholdsConfig {
        private double lowMax = 0.30;
        private double moderateMax = 0.60;
        private double highMax = 0.85;
    }

    @Getter @Setter
    public static class MlService {
        private String url = "http://localhost:8000";
        private String internalToken = "dev-internal-token";
        private long connectTimeoutSeconds = 2;
        private long readTimeoutSeconds = 8;
    }

    @Getter @Setter
    public static class Security {
        private String jwtPublicKey = "";
        private String allowedOrigins = "http://localhost:3000";
    }

    @Getter @Setter
    public static class Data {
        private double clinicalWarningThresholdMmol = 20.0;
    }

    /** Phase 13: LLM explanation layer configuration. */
    @Getter @Setter
    public static class Ai {
        private Explanation explanation = new Explanation();

        @Getter @Setter
        public static class Explanation {
            /** Master switch — set to false to disable LLM calls entirely. */
            private boolean enabled = true;
            /** Hard timeout for a single LLM call (seconds). */
            private long timeoutSeconds = 5;
        }
    }

    /** Converts config to domain value object. */
    public RiskThresholds getRiskThresholds() {
        return new RiskThresholds(
                prediction.getRiskThresholds().getLowMax(),
                prediction.getRiskThresholds().getModerateMax(),
                prediction.getRiskThresholds().getHighMax());
    }
}
