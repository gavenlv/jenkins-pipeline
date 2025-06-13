package modules

/**
 * Environment Configuration Manager for handling multi-environment configurations
 */
class EnvironmentConfigManager {
    private BasePipeline pipeline
    private Map<String, Map> environmentConfigs = [:]
    
    /**
     * Initialize environment configuration manager
     * @param pipeline Base pipeline instance
     * @param config Global configuration
     */
    EnvironmentConfigManager(BasePipeline pipeline, Map config = [:]) {
        this.pipeline = pipeline
        this.environmentConfigs = loadEnvironmentConfigs(config)
        pipeline.script.echo "Initialized environment configuration manager, supported environments: ${environmentConfigs.keySet()}"
    }
    
    /**
     * Load environment configurations
     * @param globalConfig Global configuration
     * @return Environment configuration mapping
     */
    private Map<String, Map> loadEnvironmentConfigs(Map globalConfig) {
        def configs = [:]
        
        // Default environment configurations
        def defaultEnvironments = ['dev', 'sit', 'uat', 'prod']
        defaultEnvironments.each { env ->
            configs[env] = [
                name: env,
                displayName: env.toUpperCase(),
                namespace: "${globalConfig.projectName}-${env}",
                requiresApproval: env in ['uat', 'prod'],
                autoPromote: env in ['dev', 'sit'],
                deploymentStrategy: env == 'prod' ? 'blue-green' : 'rolling-update',
                replicas: getDefaultReplicasForEnvironment(env),
                resources: getDefaultResourcesForEnvironment(env)
            ]
        }
        
        // Merge user-defined configurations
        if (globalConfig.environments) {
            globalConfig.environments.each { envConfig ->
                def envName = envConfig.name
                if (configs[envName]) {
                    configs[envName].putAll(envConfig)
                } else {
                    configs[envName] = envConfig
                }
            }
        }
        
        // Add environment-specific service configurations
        configs.each { envName, envConfig ->
            envConfig.nexus = buildNexusConfig(globalConfig, envName)
            envConfig.sonarqube = buildSonarQubeConfig(globalConfig, envName)
            envConfig.kubernetes = buildKubernetesConfig(globalConfig, envName)
            envConfig.monitoring = buildMonitoringConfig(globalConfig, envName)
        }
        
        return configs
    }
    
    /**
     * Get default replicas for environment
     */
    private int getDefaultReplicasForEnvironment(String environment) {
        switch (environment) {
            case 'dev': return 1
            case 'sit': return 1
            case 'uat': return 2
            case 'prod': return 3
            default: return 1
        }
    }
    
    /**
     * Get default resource configuration for environment
     */
    private Map getDefaultResourcesForEnvironment(String environment) {
        switch (environment) {
            case 'dev':
                return [
                    requests: [cpu: '100m', memory: '256Mi'],
                    limits: [cpu: '500m', memory: '512Mi']
                ]
            case 'sit':
                return [
                    requests: [cpu: '200m', memory: '512Mi'],
                    limits: [cpu: '1000m', memory: '1Gi']
                ]
            case 'uat':
                return [
                    requests: [cpu: '500m', memory: '1Gi'],
                    limits: [cpu: '2000m', memory: '2Gi']
                ]
            case 'prod':
                return [
                    requests: [cpu: '1000m', memory: '2Gi'],
                    limits: [cpu: '4000m', memory: '4Gi']
                ]
            default:
                return [
                    requests: [cpu: '100m', memory: '256Mi'],
                    limits: [cpu: '500m', memory: '512Mi']
                ]
        }
    }
    
    /**
     * Build Nexus configuration for environment
     */
    private Map buildNexusConfig(Map globalConfig, String environment) {
        def baseConfig = globalConfig.nexus ?: [:]
        def envSpecificConfig = [:]
        
        // Environment-specific Nexus configuration
        switch (environment) {
            case 'dev':
                envSpecificConfig = [
                    url: baseConfig.devUrl ?: "${baseConfig.url}/dev",
                    repositories: [
                        maven: 'maven-snapshots',
                        docker: 'docker-snapshots',
                        npm: 'npm-snapshots'
                    ]
                ]
                break
            case 'sit':
                envSpecificConfig = [
                    url: baseConfig.sitUrl ?: "${baseConfig.url}/sit",
                    repositories: [
                        maven: 'maven-sit',
                        docker: 'docker-sit',
                        npm: 'npm-sit'
                    ]
                ]
                break
            case 'uat':
                envSpecificConfig = [
                    url: baseConfig.uatUrl ?: "${baseConfig.url}/uat",
                    repositories: [
                        maven: 'maven-uat',
                        docker: 'docker-uat',
                        npm: 'npm-uat'
                    ]
                ]
                break
            case 'prod':
                envSpecificConfig = [
                    url: baseConfig.prodUrl ?: "${baseConfig.url}/prod",
                    repositories: [
                        maven: 'maven-releases',
                        docker: 'docker-releases',
                        npm: 'npm-releases'
                    ]
                ]
                break
            default:
                envSpecificConfig = baseConfig
        }
        
        return baseConfig + envSpecificConfig
    }
    
    /**
     * Build SonarQube configuration for environment
     */
    private Map buildSonarQubeConfig(Map globalConfig, String environment) {
        def baseConfig = globalConfig.sonarqube ?: [:]
        
        return baseConfig + [
            projectKey: "${globalConfig.projectName}-${environment}",
            qualityGate: environment == 'prod' ? 'Production' : 'Default',
            branchAnalysis: environment in ['dev', 'sit']
        ]
    }
    
    /**
     * Build Kubernetes configuration for environment
     */
    private Map buildKubernetesConfig(Map globalConfig, String environment) {
        def baseConfig = globalConfig.kubernetes ?: [:]
        
        return baseConfig + [
            namespace: "${globalConfig.projectName}-${environment}",
            context: "${environment}-cluster",
            ingressClass: environment == 'prod' ? 'nginx-prod' : 'nginx-dev',
            storageClass: environment == 'prod' ? 'fast-ssd' : 'standard'
        ]
    }
    
    /**
     * Build monitoring configuration for environment
     */
    private Map buildMonitoringConfig(Map globalConfig, String environment) {
        def baseConfig = globalConfig.monitoring ?: [:]
        
        return baseConfig + [
            metricsEnabled: true,
            alerting: environment in ['uat', 'prod'],
            dashboardUrl: "${baseConfig.grafanaUrl}/d/${globalConfig.projectName}-${environment}",
            logLevel: environment == 'prod' ? 'WARN' : 'INFO'
        ]
    }
    
    /**
     * Get environment configuration
     * @param environment Environment name
     * @return Environment configuration
     */
    Map getEnvironmentConfig(String environment) {
        def config = environmentConfigs[environment]
        if (!config) {
            pipeline.script.error "Environment ${environment} configuration not found. Available environments: ${environmentConfigs.keySet()}"
        }
        return config
    }
    
    /**
     * Get all available environments
     * @return List of environment names
     */
    List<String> getAvailableEnvironments() {
        return environmentConfigs.keySet() as List
    }
    
    /**
     * Check if environment requires approval
     * @param environment Environment name
     * @return Whether approval is required
     */
    boolean requiresApproval(String environment) {
        return getEnvironmentConfig(environment).requiresApproval ?: false
    }
    
    /**
     * Check if environment supports auto-promotion
     * @param environment Environment name
     * @return Whether auto-promotion is supported
     */
    boolean supportsAutoPromotion(String environment) {
        return getEnvironmentConfig(environment).autoPromote ?: false
    }
    
    /**
     * Get deployment strategy for environment
     * @param environment Environment name
     * @return Deployment strategy
     */
    String getDeploymentStrategy(String environment) {
        return getEnvironmentConfig(environment).deploymentStrategy ?: 'rolling-update'
    }
    
    /**
     * Validate environment configuration
     * @param environment Environment name
     * @return Validation result
     */
    Map validateEnvironmentConfig(String environment) {
        def config = getEnvironmentConfig(environment)
        def validationResult = [valid: true, errors: [], warnings: []]
        
        // Check required configurations
        if (!config.nexus?.url) {
            validationResult.errors << "Nexus URL is not configured"
        }
        
        if (!config.kubernetes?.namespace) {
            validationResult.warnings << "Kubernetes namespace is not configured, will use default value"
        }
        
        if (environment == 'prod' && !config.requiresApproval) {
            validationResult.warnings << "Production environment should enable approval process"
        }
        
        validationResult.valid = validationResult.errors.isEmpty()
        return validationResult
    }
} 