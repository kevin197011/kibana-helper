package io.github.devops;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting Kibana Utils Demo...");
        
        try {
            // 从YAML配置文件加载配置
            System.out.println("📄 Loading configuration from YAML...");
            KibanaConfig config = KibanaConfig.loadDefault();
            
            // 创建KibanaUtils实例
            KibanaUtils kibanaUtils = new KibanaUtils(
                    config.getProject().getName(),
                    config.getProject().getEnvironment(),
                    config.getKibana().getBaseUrl(),
                    config.getKibana().getUsername(),
                    config.getKibana().getPassword()
            );
            
            // 设置配置到KibanaUtils
            kibanaUtils.setConfig(config);
            
            System.out.println("✅ KibanaUtils instance created successfully!");
            System.out.println("   Project: " + config.getProject().getName());
            System.out.println("   Environment: " + config.getProject().getEnvironment());
            System.out.println("   Space Name: " + kibanaUtils.getSpaceName());
            System.out.println("   Kibana URL: " + config.getKibana().getBaseUrl());
            
            // 根据配置文件匹配规则获取并显示索引清单
            System.out.println("\n🔍 Fetching indices based on configuration patterns...");
            if (config.getSettings().isShowDetailedMatching()) {
                kibanaUtils.displayConfigBasedIndicesSummary();
            } else {
                kibanaUtils.displayProjectIndicesSummary();
            }
            
            // 询问用户是否继续执行操作
            System.out.println("\n⚡ Ready to execute configured operations...");
            
            // 根据配置执行操作
            if (config.getSettings().isAutoCreateSpace()) {
                System.out.println("\n📁 Ensuring Kibana space exists...");
                kibanaUtils.ensureSpace();
            }
            
            if (config.getSettings().isAutoAddIndices()) {
                System.out.println("\n📊 Adding data views based on combined matching indices...");
                kibanaUtils.addCombinedMatchingDataViews();
            }
            
            if (config.getSettings().isAutoCreateRole()) {
                System.out.println("\n👤 Creating project role...");
                kibanaUtils.createRole();
            }
            
            if (config.getSettings().isAutoCreateUser()) {
                System.out.println("\n🔐 Creating project user...");
                kibanaUtils.createUser();
            }
            
            System.out.println("🎉 All operations completed successfully!");
            
        } catch (Exception e) {
            System.err.println("❌ Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}