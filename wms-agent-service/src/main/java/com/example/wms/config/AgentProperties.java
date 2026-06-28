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
    private Memory memory = new Memory();
    private Llm llm = new Llm();

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

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public static class Rag {
        private boolean enabled = false;
        private String provider = "local";
        private String qdrantUrl = "http://127.0.0.1:6333";
        private String collection = "wms_agent_memory";

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

        public String getQdrantUrl() {
            return qdrantUrl;
        }

        public void setQdrantUrl(String qdrantUrl) {
            this.qdrantUrl = qdrantUrl;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }
    }

    public static class Memory {
        private Redis redis = new Redis();

        public Redis getRedis() {
            return redis;
        }

        public void setRedis(Redis redis) {
            this.redis = redis;
        }
    }

    public static class Redis {
        private boolean enabled = false;
        private String url = "redis://127.0.0.1:6379";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class Llm {
        private String provider = "disabled";
        private String model = "local-rule";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
