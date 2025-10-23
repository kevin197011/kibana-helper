package io.github.devops;

import java.io.*;
import java.net.http.*;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Java implementation of KibanaUtils for interacting with Kibana API
 */
public class KibanaUtils {
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String projectName;
    private final String projectEnv;
    private final String spaceName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private KibanaConfig config;

    public KibanaUtils(String projectName, String projectEnv, String baseUrl, String username, String password) {
        this.projectName = projectName;
        this.projectEnv = projectEnv;
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.spaceName = (projectName + "-" + projectEnv).toLowerCase();
        this.objectMapper = new ObjectMapper();
        this.httpClient = createHttpClient();
    }
    
    /**
     * Set configuration for advanced index matching
     */
    public void setConfig(KibanaConfig config) {
        this.config = config;
    }
    
    /**
     * Get the space name (for debugging/verification)
     */
    public String getSpaceName() {
        return this.spaceName;
    }

    /**
     * Create HTTP client with SSL verification disabled and basic auth
     */
    private HttpClient createHttpClient() {
        try {
            // Create trust-all SSL context (for testing only!)
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HTTP client", e);
        }
    }

    /**
     * Create basic auth header
     */
    private String getBasicAuthHeader() {
        String auth = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes());
    }

    /**
     * Ensure the Kibana space exists
     */
    public void ensureSpace() throws Exception {
        String url = baseUrl + "/api/spaces/space/" + spaceName;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", getBasicAuthHeader())
                .header("kbn-xsrf", "true")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            // Space doesn't exist, create it
            createSpace();
        } else if (response.statusCode() == 200) {
            System.out.println("ℹ️ Space " + spaceName + " already exists, skipping creation.");
        } else {
            System.out.println("❌ Error checking space: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Create a new Kibana space
     */
    private void createSpace() throws Exception {
        String url = baseUrl + "/api/spaces/space";

        ObjectNode createBody = objectMapper.createObjectNode();
        createBody.put("id", spaceName);
        createBody.put("name", capitalize(spaceName));
        createBody.put("description", "Space for " + projectName + " " + projectEnv);
        createBody.set("disabledFeatures", objectMapper.createArrayNode());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", getBasicAuthHeader())
                .header("Content-Type", "application/json")
                .header("kbn-xsrf", "true")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(createBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("✅ Space " + spaceName + " created successfully!");
        } else {
            System.out.println("❌ Failed to create space " + spaceName + ": " + response.body());
        }
    }

    /**
     * Fetch all indices from Kibana
     */
    public List<String> getIndices() throws Exception {
        String url = baseUrl + "/api/index_management/indices";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", getBasicAuthHeader())
                .header("kbn-xsrf", "true")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonNode = objectMapper.readTree(response.body());
            List<String> indices = new ArrayList<>();
            for (JsonNode item : jsonNode) {
                indices.add(item.get("name").asText());
            }
            return indices;
        } else {
            throw new RuntimeException("Failed to fetch indices: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Delete all dataviews in space
     */
    public void deleteDataviews() throws Exception {
        String url = baseUrl + "/s/" + spaceName + "/api/content_management/rpc/delete";
        List<JsonNode> dataviews = getDataviews();

        for (JsonNode dataview : dataviews) {
            ObjectNode deleteBody = objectMapper.createObjectNode();
            deleteBody.put("contentTypeId", "index-pattern");
            deleteBody.put("id", dataview.get("id").asText());
            deleteBody.set("options", objectMapper.createObjectNode().put("force", true));
            deleteBody.put("version", 1);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", getBasicAuthHeader())
                    .header("Content-Type", "application/json")
                    .header("kbn-xsrf", "true")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(deleteBody)))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /**
     * Add a new index-pattern to Kibana
     */
    public void addIndex(String indexName) throws Exception {
        String uuid = UUID.randomUUID().toString();
        String url = baseUrl + "/s/" + spaceName + "/api/content_management/rpc/create";

        ObjectNode indexBody = createIndexBody(indexName, uuid);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", getBasicAuthHeader())
                .header("Content-Type", "application/json")
                .header("kbn-xsrf", "true")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(indexBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            System.out.println("✅ " + indexName + " index creation successful!");
        } else {
            System.out.println("❌ " + indexName + " index creation failed. Error: " + response.statusCode() + " - " + response.body());
        }
    }
  
  /**
     * Get indices based on configuration file patterns
     */
    public Map<String, List<String>> getConfigBasedIndices() throws Exception {
        List<String> allIndices = getIndices();
        Map<String, List<String>> categorizedIndices = new HashMap<>();
        
        if (config == null || config.getIndexMatching() == null) {
            // Fallback to default behavior - use project and environment names
            return getProjectIndicesAsConfigFormat();
        }
        
        KibanaConfig.IndexMatchingSettings matching = config.getIndexMatching();
        
        List<String> projectMatches = new ArrayList<>();
        List<String> environmentMatches = new ArrayList<>();
        List<String> dateMatches = new ArrayList<>();
        List<String> customMatches = new ArrayList<>();
        List<String> combinedMatches = new ArrayList<>();
        List<String> excludedIndices = new ArrayList<>();
        
        for (String index : allIndices) {
            // 检查排除模式
            boolean shouldExclude = false;
            if (matching.getExcludePatterns() != null) {
                for (String excludePattern : matching.getExcludePatterns()) {
                    if (Pattern.compile(excludePattern, Pattern.CASE_INSENSITIVE).matcher(index).matches()) {
                        shouldExclude = true;
                        excludedIndices.add(index);
                        break;
                    }
                }
            }
            
            if (shouldExclude) continue;
            
            boolean matchesProject = false;
            boolean matchesEnvironment = false;
            boolean matchesDate = false;
            boolean matchesCustom = false;
            
            // 检查项目模式
            if (matching.getProjectPatterns() != null) {
                for (String pattern : matching.getProjectPatterns()) {
                    if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(index).matches()) {
                        matchesProject = true;
                        projectMatches.add(index);
                        break;
                    }
                }
            }
            
            // 检查环境模式
            if (matching.getEnvironmentPatterns() != null) {
                for (String pattern : matching.getEnvironmentPatterns()) {
                    if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(index).matches()) {
                        matchesEnvironment = true;
                        environmentMatches.add(index);
                        break;
                    }
                }
            }
            
            // 检查日期模式
            if (matching.getDatePatterns() != null) {
                for (String pattern : matching.getDatePatterns()) {
                    if (Pattern.compile(pattern).matcher(index).matches()) {
                        matchesDate = true;
                        dateMatches.add(index);
                        break;
                    }
                }
            }
            
            // 检查自定义模式
            if (matching.getCustomPatterns() != null) {
                for (String pattern : matching.getCustomPatterns()) {
                    if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(index).matches()) {
                        matchesCustom = true;
                        customMatches.add(index);
                        break;
                    }
                }
            }
            
            // 组合匹配（项目 + 环境）
            if (matchesProject && matchesEnvironment) {
                combinedMatches.add(index);
            }
        }
        
        categorizedIndices.put("project_matches", projectMatches);
        categorizedIndices.put("environment_matches", environmentMatches);
        categorizedIndices.put("date_matches", dateMatches);
        categorizedIndices.put("custom_matches", customMatches);
        categorizedIndices.put("combined_matches", combinedMatches);
        categorizedIndices.put("excluded_indices", excludedIndices);
        
        return categorizedIndices;
    }
    
    /**
     * Display configuration-based indices summary
     */
    public void displayConfigBasedIndicesSummary() throws Exception {
        System.out.println("\n📋 Configuration-Based Indices Summary");
        System.out.println("=" .repeat(60));
        System.out.println("Project: " + projectName);
        System.out.println("Environment: " + projectEnv);
        System.out.println("Today's Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        
        if (config != null && config.getIndexMatching() != null) {
            KibanaConfig.IndexMatchingSettings matching = config.getIndexMatching();
            System.out.println("Project Patterns: " + matching.getProjectPatterns());
            System.out.println("Environment Patterns: " + matching.getEnvironmentPatterns());
            System.out.println("Date Patterns: " + matching.getDatePatterns());
            System.out.println("Custom Patterns: " + matching.getCustomPatterns());
            System.out.println("Exclude Patterns: " + matching.getExcludePatterns());
        }
        System.out.println();
        
        Map<String, List<String>> indices = getConfigBasedIndices();
        
        System.out.println("🎯 Combined matches (Project + Environment) (" + indices.get("combined_matches").size() + "):");
        for (String index : indices.get("combined_matches")) {
            System.out.println("  ✅ " + index);
        }
        
//        System.out.println("\n📁 Project pattern matches (" + indices.get("project_matches").size() + "):");
//        for (String index : indices.get("project_matches")) {
//            System.out.println("  🔍 " + index);
//        }
//
//        System.out.println("\n🌍 Environment pattern matches (" + indices.get("environment_matches").size() + "):");
//        for (String index : indices.get("environment_matches")) {
//            System.out.println("  🏷️ " + index);
//        }
//
//        System.out.println("\n📅 Date pattern matches (" + indices.get("date_matches").size() + "):");
//        for (String index : indices.get("date_matches")) {
//            System.out.println("  🗓️ " + index);
//        }
//
//        System.out.println("\n🎨 Custom pattern matches (" + indices.get("custom_matches").size() + "):");
//        for (String index : indices.get("custom_matches")) {
//            System.out.println("  ⭐ " + index);
//        }
//
//        if (!indices.get("excluded_indices").isEmpty()) {
//            System.out.println("\n❌ Excluded indices (" + indices.get("excluded_indices").size() + "):");
//            for (String index : indices.get("excluded_indices")) {
//                System.out.println("  🚫 " + index);
//            }
//        }
        
        System.out.println("\n" + "=" .repeat(60));
    }

    /**
     * Get all project-related indices with detailed information
     */
    public Map<String, List<String>> getProjectIndices() throws Exception {
        List<String> allIndices = getIndices();
        Map<String, List<String>> categorizedIndices = new HashMap<>();
        
        Pattern projectPattern = Pattern.compile(".*" + projectName + ".*", Pattern.CASE_INSENSITIVE);
        Pattern envPattern = Pattern.compile(".*" + projectEnv + ".*", Pattern.CASE_INSENSITIVE);
        
        // 分类索引
        List<String> projectIndices = new ArrayList<>();
        List<String> envIndices = new ArrayList<>();
        List<String> bothIndices = new ArrayList<>();
        List<String> todayIndices = new ArrayList<>();
        
        String todayPattern = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        Pattern datePattern = Pattern.compile(".*" + todayPattern + ".*");
        
        for (String index : allIndices) {
            boolean matchesProject = projectPattern.matcher(index).matches();
            boolean matchesEnv = envPattern.matcher(index).matches();
            boolean matchesToday = datePattern.matcher(index).matches();
            
            if (matchesProject && matchesEnv) {
                bothIndices.add(index);
                if (matchesToday) {
                    todayIndices.add(index);
                }
            } else if (matchesProject) {
                projectIndices.add(index);
            } else if (matchesEnv) {
                envIndices.add(index);
            }
        }
        
        categorizedIndices.put("project_only", projectIndices);
        categorizedIndices.put("env_only", envIndices);
        categorizedIndices.put("project_and_env", bothIndices);
        categorizedIndices.put("today_matching", todayIndices);
        
        return categorizedIndices;
    }
    
    /**
     * Convert project indices format to config-based format for compatibility
     */
    private Map<String, List<String>> getProjectIndicesAsConfigFormat() throws Exception {
        Map<String, List<String>> projectIndices = getProjectIndices();
        Map<String, List<String>> configFormat = new HashMap<>();
        
        // Map traditional format to config format
        configFormat.put("project_matches", projectIndices.getOrDefault("project_only", new ArrayList<>()));
        configFormat.put("environment_matches", projectIndices.getOrDefault("env_only", new ArrayList<>()));
        configFormat.put("date_matches", new ArrayList<>()); // Empty for now
        configFormat.put("custom_matches", new ArrayList<>()); // Empty for now
        configFormat.put("combined_matches", projectIndices.getOrDefault("project_and_env", new ArrayList<>()));
        configFormat.put("excluded_indices", new ArrayList<>()); // Empty for now
        
        return configFormat;
    }

    /**
     * Display project indices summary
     */
    public void displayProjectIndicesSummary() throws Exception {
        System.out.println("\n📋 Project Indices Summary");
        System.out.println("=" .repeat(50));
        System.out.println("Project: " + projectName);
        System.out.println("Environment: " + projectEnv);
        System.out.println("Today's Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        System.out.println();
        
        Map<String, List<String>> indices = getProjectIndices();
        
        System.out.println("📊 Indices matching project + environment (" + indices.get("project_and_env").size() + "):");
        for (String index : indices.get("project_and_env")) {
            System.out.println("  ✓ " + index);
        }
        
        System.out.println("\n🗓️ Today's matching indices (" + indices.get("today_matching").size() + "):");
        for (String index : indices.get("today_matching")) {
            System.out.println("  📅 " + index);
        }
        
        if (!indices.get("project_only").isEmpty()) {
            System.out.println("\n🔍 Project-only indices (" + indices.get("project_only").size() + "):");
            for (String index : indices.get("project_only")) {
                System.out.println("  📁 " + index);
            }
        }
        
        if (!indices.get("env_only").isEmpty()) {
            System.out.println("\n🌍 Environment-only indices (" + indices.get("env_only").size() + "):");
            for (String index : indices.get("env_only")) {
                System.out.println("  🏷️ " + index);
            }
        }
        
        System.out.println("\n" + "=" .repeat(50));
    }

    /**
     * Extract base names from indices by removing date suffixes and deduplicate
     */
    public Set<String> extractBaseNames(List<String> indices) {
        Set<String> baseNames = new HashSet<>();
        
        System.out.println("🔄 Extracting base names and removing date suffixes:");
        
        for (String index : indices) {
            String baseName = index;
            
            // 移除各种日期格式的后缀
            baseName = baseName.replaceAll("-\\d{4}\\.\\d{2}\\.\\d{2}.*$", "");  // -yyyy.MM.dd
            baseName = baseName.replaceAll("-\\d{4}-\\d{2}-\\d{2}.*$", "");     // -yyyy-MM-dd
            baseName = baseName.replaceAll("_\\d{4}\\.\\d{2}\\.\\d{2}.*$", ""); // _yyyy.MM.dd
            baseName = baseName.replaceAll("_\\d{4}-\\d{2}-\\d{2}.*$", "");     // _yyyy-MM-dd
            baseName = baseName.replaceAll("\\.\\d{4}\\.\\d{2}\\.\\d{2}.*$", ""); // .yyyy.MM.dd
            baseName = baseName.replaceAll("\\d{4}\\.\\d{2}\\.\\d{2}$", "");    // yyyy.MM.dd at end
            baseName = baseName.replaceAll("\\d{4}-\\d{2}-\\d{2}$", "");       // yyyy-MM-dd at end
            
            // 转换为小写
            baseName = baseName.toLowerCase();
            
            if (!baseName.isEmpty() && !baseName.equals(index.toLowerCase())) {
                baseNames.add(baseName);
                System.out.println("  📝 " + index + " → " + baseName + " (lowercase)");
            } else if (!baseName.isEmpty()) {
                baseNames.add(baseName);
                System.out.println("  📝 " + index + " → " + baseName + " (lowercase, no date suffix)");
            }
        }
        
        return baseNames;
    }
    
    /**
     * Add data views based on combined matching indices (project + environment)
     */
    public void addCombinedMatchingDataViews() throws Exception {
        ensureSpace();
        deleteDataviews();

        Map<String, List<String>> indices = getConfigBasedIndices();
        List<String> combinedMatches = indices.get("combined_matches");
        
        System.out.println("\n📊 Processing " + combinedMatches.size() + " combined matching indices...");
        
        if (combinedMatches.isEmpty()) {
            System.out.println("⚠️ No combined matching indices found. Please check your configuration patterns.");
            return;
        }
        
        Set<String> baseNames = extractBaseNames(combinedMatches);
        
        System.out.println("\n✨ Found " + baseNames.size() + " unique base index patterns:");
        for (String baseName : baseNames) {
            System.out.println("  🎯 " + baseName);
        }
        
        System.out.println("\n🚀 Creating data views...");
        for (String baseName : baseNames) {
            addIndex(baseName);
        }
        
        System.out.println("\n✅ Successfully created " + baseNames.size() + " data views for log viewing!");
    }

    /**
     * Add all matching indices to Kibana (enhanced version)
     */
    public void addAllIndex() throws Exception {
        ensureSpace();
        deleteDataviews();

        Map<String, List<String>> indices;
        List<String> targetIndices;
        
        // 优先使用配置基础的组合匹配
        if (config != null && config.getIndexMatching() != null) {
            indices = getConfigBasedIndices();
            targetIndices = indices.get("combined_matches");
            System.out.println("📊 Using configuration-based combined matching...");
        } else {
            indices = getProjectIndices();
            targetIndices = indices.get("project_and_env");
            System.out.println("📊 Using legacy project+environment matching...");
        }
        
        if (targetIndices.isEmpty()) {
            System.out.println("⚠️ No matching indices found. Please check your configuration.");
            return;
        }
        
        System.out.println("🔄 Processing " + targetIndices.size() + " matching indices...");
        Set<String> baseNames = extractBaseNames(targetIndices);
        
        System.out.println("\n✨ Creating " + baseNames.size() + " unique data views...");
        for (String baseName : baseNames) {
            addIndex(baseName);
        }
        
        System.out.println("\n✅ Data views created successfully! You can now view logs in Kibana.");
    }
 
   /**
     * Create a role for this project
     */
    public void createRole() throws Exception {
        String url = baseUrl + "/api/security/role/" + projectName + "?createOnly=true";

        ObjectNode roleBody = objectMapper.createObjectNode();

        // Elasticsearch section
        ObjectNode elasticsearch = objectMapper.createObjectNode();
        elasticsearch.set("cluster", objectMapper.createArrayNode());
        elasticsearch.set("run_as", objectMapper.createArrayNode());

        ArrayNode indices = objectMapper.createArrayNode();
        ObjectNode indexRule = objectMapper.createObjectNode();
        ArrayNode names = objectMapper.createArrayNode();
        names.add("*" + projectName + "*");
        indexRule.set("names", names);
        ArrayNode privileges = objectMapper.createArrayNode();
        privileges.add("read");
        indexRule.set("privileges", privileges);
        indices.add(indexRule);
        elasticsearch.set("indices", indices);

        roleBody.set("elasticsearch", elasticsearch);

        // Kibana section
        ArrayNode kibana = objectMapper.createArrayNode();
        ObjectNode kibanaRule = objectMapper.createObjectNode();
        ArrayNode spaces = objectMapper.createArrayNode();
        spaces.add(projectName + "-prod");
        spaces.add(projectName + "-uat");
        kibanaRule.set("spaces", spaces);
        kibanaRule.set("base", objectMapper.createArrayNode());

        ObjectNode feature = objectMapper.createObjectNode();
        ArrayNode discoverPrivs = objectMapper.createArrayNode();
        discoverPrivs.add("read");
        feature.set("discover", discoverPrivs);
        kibanaRule.set("feature", feature);

        kibana.add(kibanaRule);
        roleBody.set("kibana", kibana);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", getBasicAuthHeader())
                .header("Content-Type", "application/json")
                .header("kbn-xsrf", "true")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(roleBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("✅ Created " + projectName + " role successfully!");
    }

    /**
     * Create a user for this project
     */
    public void createUser() throws Exception {
        String url = baseUrl + "/internal/security/users/" + projectName;

        ObjectNode userBody = objectMapper.createObjectNode();
        userBody.put("password", "123456");
        userBody.put("username", projectName);
        userBody.put("full_name", projectName);
        userBody.put("email", projectName + "@devops.io");

        ArrayNode roles = objectMapper.createArrayNode();
        roles.add(projectName);
        userBody.set("roles", roles);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", getBasicAuthHeader())
                .header("Content-Type", "application/json")
                .header("kbn-xsrf", "true")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(userBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("✅ Created " + projectName + " user successfully!");
    }

    /**
     * Fetch all index-patterns (data views)
     */
    private List<JsonNode> getDataviews() throws Exception {
        String url = baseUrl + "/s/" + spaceName + "/api/content_management/rpc/search";

        ObjectNode searchBody = objectMapper.createObjectNode();
        searchBody.put("contentTypeId", "index-pattern");

        ObjectNode query = objectMapper.createObjectNode();
        query.put("limit", 10000);
        searchBody.set("query", query);

        ObjectNode options = objectMapper.createObjectNode();
        ArrayNode fields = objectMapper.createArrayNode();
        fields.add("title");
        fields.add("type");
        fields.add("typeMeta");
        fields.add("name");
        options.set("fields", fields);
        searchBody.set("options", options);
        searchBody.put("version", 1);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", getBasicAuthHeader())
                .header("Content-Type", "application/json")
                .header("kbn-xsrf", "true")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(searchBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonNode = objectMapper.readTree(response.body());
            JsonNode hits = jsonNode.get("result").get("result").get("hits");
            List<JsonNode> dataviews = new ArrayList<>();
            for (JsonNode hit : hits) {
                dataviews.add(hit);
            }
            return dataviews;
        } else {
            throw new RuntimeException("Failed to fetch dataviews: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Create the body for index-pattern creation
     */
    private ObjectNode createIndexBody(String indexName, String uuid) {
        ObjectNode indexBody = objectMapper.createObjectNode();
        indexBody.put("contentTypeId", "index-pattern");

        ObjectNode data = objectMapper.createObjectNode();
        data.put("fieldAttrs", "{}");
        data.put("title", indexName + "*");
        data.put("timeFieldName", "@timestamp");
        data.put("sourceFilters", "[]");
        data.put("fields", "[]");
        data.put("fieldFormatMap", "{}");
        data.put("runtimeFieldMap", "{}");
        data.put("name", indexName);
        data.put("allowHidden", false);
        indexBody.set("data", data);

        ObjectNode options = objectMapper.createObjectNode();
        options.put("id", uuid);
        options.put("overwrite", false);
        indexBody.set("options", options);
        indexBody.put("version", 1);

        return indexBody;
    }

    /**
     * Capitalize first letter of string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        try {
            KibanaUtils kibana = new KibanaUtils(
                    "myproject",
                    "dev",
                    "https://your-kibana-url",
                    "username",
                    "password"
            );

            // Example usage
            kibana.ensureSpace();
            kibana.addCombinedMatchingDataViews();
            kibana.createRole();
            kibana.createUser();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}