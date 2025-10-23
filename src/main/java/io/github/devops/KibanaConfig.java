package io.github.devops;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.util.List;

/**
 * Configuration class for Kibana settings
 */
public class KibanaConfig {
    
    @JsonProperty("kibana")
    private KibanaSettings kibana;
    
    @JsonProperty("project")
    private ProjectSettings project;
    
    @JsonProperty("settings")
    private AppSettings settings;
    
    @JsonProperty("indexMatching")
    private IndexMatchingSettings indexMatching;
    
    public static class IndexMatchingSettings {
        @JsonProperty("projectPatterns")
        private List<String> projectPatterns;
        
        @JsonProperty("environmentPatterns")
        private List<String> environmentPatterns;
        
        @JsonProperty("datePatterns")
        private List<String> datePatterns;
        
        @JsonProperty("customPatterns")
        private List<String> customPatterns;
        
        @JsonProperty("excludePatterns")
        private List<String> excludePatterns;
        
        // Getters and setters
        public List<String> getProjectPatterns() { return projectPatterns; }
        public void setProjectPatterns(List<String> projectPatterns) { this.projectPatterns = projectPatterns; }
        
        public List<String> getEnvironmentPatterns() { return environmentPatterns; }
        public void setEnvironmentPatterns(List<String> environmentPatterns) { this.environmentPatterns = environmentPatterns; }
        
        public List<String> getDatePatterns() { return datePatterns; }
        public void setDatePatterns(List<String> datePatterns) { this.datePatterns = datePatterns; }
        
        public List<String> getCustomPatterns() { return customPatterns; }
        public void setCustomPatterns(List<String> customPatterns) { this.customPatterns = customPatterns; }
        
        public List<String> getExcludePatterns() { return excludePatterns; }
        public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }
    }
    
    public static class KibanaSettings {
        @JsonProperty("baseUrl")
        private String baseUrl;
        
        @JsonProperty("username")
        private String username;
        
        @JsonProperty("password")
        private String password;
        
        // Getters and setters
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class ProjectSettings {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("environment")
        private String environment;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
    }
    
    public static class AppSettings {
        @JsonProperty("autoCreateSpace")
        private boolean autoCreateSpace = true;
        
        @JsonProperty("autoAddIndices")
        private boolean autoAddIndices = true;
        
        @JsonProperty("autoCreateRole")
        private boolean autoCreateRole = true;
        
        @JsonProperty("autoCreateUser")
        private boolean autoCreateUser = true;
        
        @JsonProperty("showDetailedMatching")
        private boolean showDetailedMatching = true;
        
        // Getters and setters
        public boolean isAutoCreateSpace() { return autoCreateSpace; }
        public void setAutoCreateSpace(boolean autoCreateSpace) { this.autoCreateSpace = autoCreateSpace; }
        
        public boolean isAutoAddIndices() { return autoAddIndices; }
        public void setAutoAddIndices(boolean autoAddIndices) { this.autoAddIndices = autoAddIndices; }
        
        public boolean isAutoCreateRole() { return autoCreateRole; }
        public void setAutoCreateRole(boolean autoCreateRole) { this.autoCreateRole = autoCreateRole; }
        
        public boolean isAutoCreateUser() { return autoCreateUser; }
        public void setAutoCreateUser(boolean autoCreateUser) { this.autoCreateUser = autoCreateUser; }
        
        public boolean isShowDetailedMatching() { return showDetailedMatching; }
        public void setShowDetailedMatching(boolean showDetailedMatching) { this.showDetailedMatching = showDetailedMatching; }
    }
    
    // Main getters and setters
    public KibanaSettings getKibana() { return kibana; }
    public void setKibana(KibanaSettings kibana) { this.kibana = kibana; }
    
    public ProjectSettings getProject() { return project; }
    public void setProject(ProjectSettings project) { this.project = project; }
    
    public AppSettings getSettings() { return settings; }
    public void setSettings(AppSettings settings) { this.settings = settings; }
    
    public IndexMatchingSettings getIndexMatching() { return indexMatching; }
    public void setIndexMatching(IndexMatchingSettings indexMatching) { this.indexMatching = indexMatching; }
    
    /**
     * Load configuration from YAML file
     */
    public static KibanaConfig loadFromYaml(String configPath) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        
        try (InputStream inputStream = KibanaConfig.class.getClassLoader().getResourceAsStream(configPath)) {
            if (inputStream == null) {
                throw new RuntimeException("Configuration file not found: " + configPath);
            }
            return mapper.readValue(inputStream, KibanaConfig.class);
        }
    }
    
    /**
     * Load default configuration
     */
    public static KibanaConfig loadDefault() throws Exception {
        return loadFromYaml("kibana-config.yml");
    }
}