package tests.unit

import spock.lang.Specification
import spock.lang.Unroll
import modules.EnvironmentConfigManager
import modules.BasePipeline

/**
 * Unit tests for EnvironmentConfigManager module
 */
class EnvironmentConfigManagerTest extends Specification {
    
    def mockScript
    def mockPipeline
    def configManager
    def testConfig
    
    def setup() {
        mockScript = Mock()
        mockPipeline = Mock(BasePipeline)
        mockPipeline.script >> mockScript
        
        testConfig = [
            projectName: 'test-app',
            nexus: [
                url: 'https://nexus.example.com',
                devUrl: 'https://nexus-dev.example.com',
                prodUrl: 'https://nexus-prod.example.com'
            ],
            sonarqube: [
                url: 'https://sonar.example.com'
            ],
            kubernetes: [
                cluster: 'test-cluster'
            ],
            monitoring: [
                grafanaUrl: 'https://grafana.example.com'
            ]
        ]
        
        configManager = new EnvironmentConfigManager(mockPipeline, testConfig)
    }
    
    def "should initialize with default environments"() {
        expect:
        configManager.getAvailableEnvironments().containsAll(['dev', 'sit', 'uat', 'prod'])
        configManager.getAvailableEnvironments().size() == 4
    }
    
    @Unroll
    def "should return correct configuration for environment #env"() {
        when:
        def envConfig = configManager.getEnvironmentConfig(env)
        
        then:
        envConfig.name == env
        envConfig.displayName == expectedDisplayName
        envConfig.namespace == "test-app-${env}"
        
        where:
        env    | expectedDisplayName
        'dev'  | 'DEV'
        'sit'  | 'SIT'
        'uat'  | 'UAT'
        'prod' | 'PROD'
    }
    
    @Unroll
    def "should return correct approval requirement for environment #env"() {
        expect:
        configManager.requiresApproval(env) == expectedApproval
        
        where:
        env    | expectedApproval
        'dev'  | false
        'sit'  | false
        'uat'  | true
        'prod' | true
    }
    
    @Unroll
    def "should return correct auto-promotion setting for environment #env"() {
        expect:
        configManager.supportsAutoPromotion(env) == expectedAutoPromotion
        
        where:
        env    | expectedAutoPromotion
        'dev'  | true
        'sit'  | true
        'uat'  | false
        'prod' | false
    }
    
    @Unroll
    def "should return correct replicas for environment #env"() {
        when:
        def envConfig = configManager.getEnvironmentConfig(env)
        
        then:
        envConfig.replicas == expectedReplicas
        
        where:
        env    | expectedReplicas
        'dev'  | 1
        'sit'  | 1
        'uat'  | 2
        'prod' | 3
    }
    
    @Unroll
    def "should return correct deployment strategy for environment #env"() {
        expect:
        configManager.getDeploymentStrategy(env) == expectedStrategy
        
        where:
        env    | expectedStrategy
        'dev'  | 'rolling-update'
        'sit'  | 'rolling-update'
        'uat'  | 'rolling-update'
        'prod' | 'blue-green'
    }
    
    def "should build correct Nexus configuration for environments"() {
        when:
        def devConfig = configManager.getEnvironmentConfig('dev')
        def prodConfig = configManager.getEnvironmentConfig('prod')
        
        then:
        devConfig.nexus.url == 'https://nexus-dev.example.com'
        devConfig.nexus.repositories.maven == 'maven-snapshots'
        devConfig.nexus.repositories.docker == 'docker-snapshots'
        
        prodConfig.nexus.url == 'https://nexus-prod.example.com'
        prodConfig.nexus.repositories.maven == 'maven-releases'
        prodConfig.nexus.repositories.docker == 'docker-releases'
    }
    
    def "should build correct SonarQube configuration"() {
        when:
        def devConfig = configManager.getEnvironmentConfig('dev')
        def prodConfig = configManager.getEnvironmentConfig('prod')
        
        then:
        devConfig.sonarqube.projectKey == 'test-app-dev'
        devConfig.sonarqube.qualityGate == 'Default'
        devConfig.sonarqube.branchAnalysis == true
        
        prodConfig.sonarqube.projectKey == 'test-app-prod'
        prodConfig.sonarqube.qualityGate == 'Production'
        prodConfig.sonarqube.branchAnalysis == false
    }
    
    def "should build correct Kubernetes configuration"() {
        when:
        def envConfig = configManager.getEnvironmentConfig('dev')
        
        then:
        envConfig.kubernetes.namespace == 'test-app-dev'
        envConfig.kubernetes.context == 'dev-cluster'
        envConfig.kubernetes.ingressClass == 'nginx-dev'
        envConfig.kubernetes.storageClass == 'standard'
    }
    
    def "should build correct monitoring configuration"() {
        when:
        def devConfig = configManager.getEnvironmentConfig('dev')
        def prodConfig = configManager.getEnvironmentConfig('prod')
        
        then:
        devConfig.monitoring.metricsEnabled == true
        devConfig.monitoring.alerting == false
        devConfig.monitoring.logLevel == 'INFO'
        devConfig.monitoring.dashboardUrl == 'https://grafana.example.com/d/test-app-dev'
        
        prodConfig.monitoring.alerting == true
        prodConfig.monitoring.logLevel == 'WARN'
    }
    
    def "should validate environment configuration correctly"() {
        when:
        def validation = configManager.validateEnvironmentConfig('dev')
        
        then:
        validation.valid == true
        validation.errors.isEmpty()
    }
    
    def "should return validation errors for missing configuration"() {
        given:
        def invalidConfig = [projectName: 'test-app']
        def invalidConfigManager = new EnvironmentConfigManager(mockPipeline, invalidConfig)
        
        when:
        def validation = invalidConfigManager.validateEnvironmentConfig('dev')
        
        then:
        validation.valid == false
        validation.errors.contains('Nexus URL is not configured')
    }
    
    def "should handle custom environment configuration"() {
        given:
        def customConfig = testConfig + [
            environments: [
                [
                    name: 'staging',
                    requiresApproval: true,
                    deploymentStrategy: 'canary',
                    replicas: 2
                ]
            ]
        ]
        def customConfigManager = new EnvironmentConfigManager(mockPipeline, customConfig)
        
        when:
        def stagingConfig = customConfigManager.getEnvironmentConfig('staging')
        
        then:
        stagingConfig.name == 'staging'
        stagingConfig.requiresApproval == true
        stagingConfig.deploymentStrategy == 'canary'
        stagingConfig.replicas == 2
    }
    
    def "should throw error for unknown environment"() {
        when:
        configManager.getEnvironmentConfig('unknown')
        
        then:
        1 * mockScript.error("Environment unknown configuration not found. Available environments: [dev, sit, uat, prod]")
    }
    
    def "should merge user-defined environment configuration with defaults"() {
        given:
        def configWithCustomEnv = testConfig + [
            environments: [
                [
                    name: 'dev',
                    customProperty: 'customValue',
                    replicas: 5  // Override default
                ]
            ]
        ]
        def customConfigManager = new EnvironmentConfigManager(mockPipeline, configWithCustomEnv)
        
        when:
        def devConfig = customConfigManager.getEnvironmentConfig('dev')
        
        then:
        devConfig.customProperty == 'customValue'
        devConfig.replicas == 5  // Should be overridden
        devConfig.autoPromote == true  // Should keep default
    }
    
    def "should handle resource configuration correctly"() {
        when:
        def devConfig = configManager.getEnvironmentConfig('dev')
        def prodConfig = configManager.getEnvironmentConfig('prod')
        
        then:
        devConfig.resources.requests.cpu == '100m'
        devConfig.resources.requests.memory == '256Mi'
        devConfig.resources.limits.cpu == '500m'
        devConfig.resources.limits.memory == '512Mi'
        
        prodConfig.resources.requests.cpu == '1000m'
        prodConfig.resources.requests.memory == '2Gi'
        prodConfig.resources.limits.cpu == '4000m'
        prodConfig.resources.limits.memory == '4Gi'
    }
    
    def "should return warnings for production environment without approval"() {
        given:
        def configWithoutApproval = testConfig + [
            environments: [
                [
                    name: 'prod',
                    requiresApproval: false
                ]
            ]
        ]
        def noApprovalConfigManager = new EnvironmentConfigManager(mockPipeline, configWithoutApproval)
        
        when:
        def validation = noApprovalConfigManager.validateEnvironmentConfig('prod')
        
        then:
        validation.valid == true
        validation.warnings.contains('Production environment should enable approval process')
    }
    
    def "should handle fallback to default Nexus configuration"() {
        given:
        def configWithoutEnvSpecificNexus = [
            projectName: 'test-app',
            nexus: [url: 'https://nexus.example.com']
        ]
        def fallbackConfigManager = new EnvironmentConfigManager(mockPipeline, configWithoutEnvSpecificNexus)
        
        when:
        def devConfig = fallbackConfigManager.getEnvironmentConfig('dev')
        
        then:
        devConfig.nexus.url == 'https://nexus.example.com/dev'  // Should append environment
        devConfig.nexus.repositories.maven == 'maven-snapshots'
    }
} 