/**
 * 本文件定义智能助手服务的可配置开关和分析参数。
 */
package com.example.wms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private boolean callApi = false;
    private int forecastDays = 30;
    private int suggestionLimit = 20;
    private Rag rag = new Rag();

    public boolean isCallApi() {
        return callApi;
    }

    public void setCallApi(boolean callApi) {
        this.callApi = callApi;
    }

    public int getForecastDays() {
        return forecastDays;
    }

    public void setForecastDays(int forecastDays) {
        this.forecastDays = forecastDays;
    }

    public int getSuggestionLimit() {
        return suggestionLimit;
    }

    public void setSuggestionLimit(int suggestionLimit) {
        this.suggestionLimit = suggestionLimit;
    }

    public Rag getRag() {
        return rag;
    }

    public void setRag(Rag rag) {
        this.rag = rag;
    }

    public static class Rag {
        private boolean enabled = false;
        private String provider = "local";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }
}
