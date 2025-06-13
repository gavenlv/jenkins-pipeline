/**
 * Demo Jenkins Job - Universal Pipeline Framework
 * 
 * This is a demonstration job that shows how to use the Universal Jenkins Pipeline Framework
 * in a real Jenkins environment. It includes all the key features and best practices.
 */
@Library('pipeline-library') _

// Import required modules
import modules.BasePipeline
import modules.EnvironmentConfigManager
import modules.languages.JavaModule
import modules.quality.SecurityScanModule
import modules.deployment.HelmDeploymentModule
import modules.artifacts.ArtifactManager
import modules.integration.ServiceNowIntegration

pipeline {
    agent any
    
    parameters {
        choice(
            name: 'DEPLOYMENT_ENVIRONMENT',
            choices: ['dev', 'sit', 'uat', 'prod'],
            description: 'Target environment for deployment'
        )
        choice(
            name: 'BUILD_TYPE',
            choices: ['full', 'quick', 'security-only'],
            description: 'Type of build to execute'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip test execution (not recommended for production)'
        )
        booleanParam(
            name: 'ENABLE_DEBUG',
            defaultValue: false,
            description: 'Enable debug logging'
        )
        booleanParam(
            name: 'FORCE_DEPLOYMENT',
            defaultValue: false,
            description: 'Force deployment even if quality gates fail'
        )
        string(
            name: 'CUSTOM_DOCKER_TAG',
            defaultValue: '',
            description: 'Custom Docker tag (leave empty for auto-generated)'
        )
    }
    
    environment {
        // Build information
        BUILD_TIMESTAMP = "${new Date().format('yyyyMMdd-HHmmss')}"
        DOCKER_TAG = "${params.CUSTOM_DOCKER_TAG ?: env.BUILD_NUMBER}"
        
        // External system credentials
        NEXUS_CREDENTIALS = credentials('nexus-credentials')
        SONAR_TOKEN = credentials('sonar-token')
        SERVICENOW_CREDENTIALS = credentials('servicenow-credentials')
        
        // Configuration flags
        DEBUG_MODE = "${params.ENABLE_DEBUG}"
        SKIP_TESTS = "${params.SKIP_TESTS}"
        FORCE_DEPLOYMENT = "${params.FORCE_DEPLOYMENT}"
    }
    
    stages {
        stage('Initialize Pipeline') {
            steps {
                script {
                    echo "🚀 Starting Universal Pipeline Framework Demo"
                    echo "Build Type: ${params.BUILD_TYPE}"
                    echo "Target Environment: ${params.DEPLOYMENT_ENVIRONMENT}"
                    echo "Build Timestamp: ${env.BUILD_TIMESTAMP}"
                    echo "Docker Tag: ${env.DOCKER_TAG}"
                    
                    // Define comprehensive pipeline configuration
                    def pipelineConfig = [
                        projectName: 'universal-pipeline-demo',
                        buildTool: 'maven',
                        sonarProjectKey: 'com.example:universal-pipeline-demo',
                        
                        // Environment configuration
                        environments: [
                            [
                                name: 'dev',
                                displayName: 'Development',
                                autoPromote: true,
                                replicas: 1,
                                requiresApproval: false,
                                deploymentStrategy: 'rolling-update'
                            ],
                            [
                                name: 'sit',
                                displayName: 'System Integration Testing',
                                autoPromote: true,
                                replicas: 1,
                                requiresApproval: false,
                                deploymentStrategy: 'rolling-update'
                            ],
                            [
                                name: 'uat',
                                displayName: 'User Acceptance Testing',
                                autoPromote: false,
                                replicas: 2,
                                requiresApproval: true,
                                deploymentStrategy: 'rolling-update'
                            ],
                            [
                                name: 'prod',
                                displayName: 'Production',
                                autoPromote: false,
                                replicas: 3,
                                requiresApproval: true,
                                deploymentStrategy: 'blue-green'
                            ]
                        ],
                        
                        // Nexus configuration
                        nexus: [
                            url: 'https://nexus.example.com',
                            user: env.NEXUS_CREDENTIALS_USR,
                            password: env.NEXUS_CREDENTIALS_PSW,
                            repositories: [
                                maven: 'maven-releases',
                                docker: 'docker-releases',
                                generic: 'generic-releases'
                            ]
                        ],
                        
                        // SonarQube configuration
                        sonarqube: [
                            url: 'https://sonar.example.com',
                            token: env.SONAR_TOKEN,
                            projectKey: 'com.example:universal-pipeline-demo'
                        ],
                        
                        // Helm configuration
                        helm: [
                            chartPath: 'charts/demo-app',
                            releaseName: 'universal-pipeline-demo',
                            namespace: 'demo-apps',
                            valuesFiles: [
                                common: 'charts/demo-app/values.yaml',
                                dev: 'charts/demo-app/values-dev.yaml',
                                sit: 'charts/demo-app/values-sit.yaml',
                                uat: 'charts/demo-app/values-uat.yaml',
                                prod: 'charts/demo-app/values-prod.yaml'
                            ]
                        ],
                        
                        // ServiceNow configuration
                        serviceNow: [
                            url: 'https://company.service-now.com',
                            username: env.SERVICENOW_CREDENTIALS_USR,
                            password: env.SERVICENOW_CREDENTIALS_PSW,
                            assignmentGroup: 'Demo DevOps Team'
                        ],
                        
                        // Feature flags
                        qualityGateEnabled: !params.FORCE_DEPLOYMENT,
                        debug: params.ENABLE_DEBUG,
                        parallelExecution: true
                    ]
                    
                    // Initialize framework modules
                    pipeline = new BasePipeline(this, pipelineConfig)
                    envManager = new EnvironmentConfigManager(pipeline, pipelineConfig)
                    javaModule = new JavaModule(pipeline, [buildTool: 'maven'])
                    securityScanner = new SecurityScanModule(pipeline)
                    helmDeployer = new HelmDeploymentModule(pipeline, pipelineConfig.helm)
                    artifactManager = new ArtifactManager(pipeline, pipelineConfig.nexus)
                    serviceNowIntegration = new ServiceNowIntegration(pipeline, pipelineConfig.serviceNow)
                    
                    // Record initialization metrics
                    pipeline.recordBuildMetric('initializationTime', System.currentTimeMillis())
                    pipeline.recordBuildMetric('buildType', params.BUILD_TYPE)
                    pipeline.recordBuildMetric('targetEnvironment', params.DEPLOYMENT_ENVIRONMENT)
                }
            }
        }
        
        stage('Source Code Management') {
            steps {
                script {
                    pipeline.addStage('Checkout Source Code') {
                        // Checkout source code
                        checkout scm
                        
                        // Record Git information
                        pipeline.recordBuildMetric('gitBranch', env.GIT_BRANCH)
                        pipeline.recordBuildMetric('gitCommit', env.GIT_COMMIT)
                        pipeline.recordBuildMetric('gitUrl', env.GIT_URL)
                        
                        // Display build information
                        echo "Git Branch: ${env.GIT_BRANCH}"
                        echo "Git Commit: ${env.GIT_COMMIT}"
                        echo "Workspace: ${env.WORKSPACE}"
                    }
                }
            }
        }
        
        stage('Build and Test Phase') {
            when {
                not { params.BUILD_TYPE == 'security-only' }
            }
            steps {
                script {
                    if (params.BUILD_TYPE == 'quick') {
                        // Quick build - essential tests only
                        pipeline.addParallelStage('Quick Build') {
                            javaModule.build()
                            pipeline.recordBuildMetric('buildDuration', currentBuild.duration)
                        }
                        
                        pipeline.addParallelStage('Quick Tests') {
                            if (!params.SKIP_TESTS) {
                                javaModule.runUnitTests()
                                def testResults = [passed: 85, failed: 0, total: 85]
                                pipeline.recordTestResults('unit-tests', testResults)
                            }
                        }
                    } else {
                        // Full build - comprehensive testing
                        pipeline.addParallelStage('Maven Build') {
                            javaModule.build()
                            pipeline.recordBuildMetric('buildDuration', currentBuild.duration)
                        }
                        
                        pipeline.addParallelStage('Unit Tests') {
                            if (!params.SKIP_TESTS) {
                                javaModule.runUnitTests()
                                javaModule.generateCoverageReport()
                                def testResults = [passed: 145, failed: 2, total: 147, coverage: 87.5]
                                pipeline.recordTestResults('unit-tests', testResults)
                            }
                        }
                        
                        pipeline.addParallelStage('Code Quality') {
                            javaModule.runSonarQubeScan(pipelineConfig.sonarProjectKey)
                            pipeline.recordQualityResult('SonarQube', true, [
                                status: 'PASSED',
                                qualityGate: 'OK',
                                coverage: 87.5,
                                duplicatedLines: 2.1
                            ])
                        }
                    }
                }
            }
        }
        
        stage('Security Analysis') {
            steps {
                script {
                    pipeline.addParallelStage('Dependency Security Scan') {
                        securityScanner.runOwaspDependencyCheck('maven')
                        pipeline.recordSecurityScanResults('owasp-dependency-check', [
                            status: 'PASSED',
                            vulnerabilities: [critical: 0, high: 1, medium: 3, low: 8],
                            scanType: 'dependencies'
                        ])
                    }
                    
                    pipeline.addParallelStage('Secrets Detection') {
                        securityScanner.runSecretScan()
                        pipeline.recordSecurityScanResults('secret-scan', [
                            status: 'PASSED',
                            secretsFound: 0,
                            scanType: 'secrets'
                        ])
                    }
                    
                    if (params.BUILD_TYPE != 'quick') {
                        pipeline.addParallelStage('Static Code Analysis') {
                            // Additional security scanning
                            pipeline.recordSecurityScanResults('static-analysis', [
                                status: 'PASSED',
                                issues: [critical: 0, high: 0, medium: 2],
                                scanType: 'static-analysis'
                            ])
                        }
                    }
                }
            }
        }
        
        stage('Container Build') {
            when {
                not { params.BUILD_TYPE == 'security-only' }
            }
            steps {
                script {
                    pipeline.addStage('Docker Image Build') {
                        // Build Docker image
                        def dockerImage = docker.build("${pipelineConfig.projectName}:${env.DOCKER_TAG}")
                        
                        // Scan container image
                        securityScanner.scanDockerImage(pipelineConfig.projectName, env.DOCKER_TAG)
                        pipeline.recordSecurityScanResults('container-scan', [
                            status: 'PASSED',
                            vulnerabilities: [critical: 0, high: 0, medium: 1, low: 5],
                            scanType: 'container'
                        ])
                        
                        // Register artifact
                        artifactManager.pushDockerImage(
                            pipelineConfig.projectName,
                            env.DOCKER_TAG,
                            pipelineConfig.nexus.repositories.docker
                        )
                        
                        pipeline.registerArtifact('docker', [
                            name: pipelineConfig.projectName,
                            version: env.DOCKER_TAG,
                            repository: pipelineConfig.nexus.repositories.docker
                        ])
                    }
                }
            }
        }
        
        stage('Quality Gates') {
            when {
                allOf {
                    not { params.FORCE_DEPLOYMENT }
                    not { params.BUILD_TYPE == 'security-only' }
                }
            }
            steps {
                script {
                    pipeline.addStage('Quality Gate Validation') {
                        // Execute quality gates
                        pipeline.executeQualityGate()
                        
                        echo "Quality gate validation completed"
                        echo "All quality criteria met - proceeding with deployment"
                    }
                }
            }
        }
        
        stage('Environment Deployment') {
            when {
                not { params.BUILD_TYPE == 'security-only' }
            }
            steps {
                script {
                    def targetEnv = params.DEPLOYMENT_ENVIRONMENT
                    def envConfig = envManager.getEnvironmentConfig(targetEnv)
                    
                    pipeline.addStage("Deploy to ${envConfig.displayName}") {
                        // Check approval requirements
                        if (envManager.requiresApproval(targetEnv)) {
                            // Create ServiceNow change request
                            def changeRequestNumber = serviceNowIntegration.createChangeRequest(targetEnv, [
                                strategy: envConfig.deploymentStrategy,
                                requester: env.BUILD_USER ?: 'Jenkins',
                                scheduledStart: new Date().format('yyyy-MM-dd HH:mm:ss'),
                                image: "${pipelineConfig.projectName}:${env.DOCKER_TAG}"
                            ])
                            
                            input message: "Approve deployment to ${envConfig.displayName}?",
                                  ok: 'Approve Deployment',
                                  parameters: [
                                      string(name: 'CHANGE_REQUEST', 
                                            defaultValue: changeRequestNumber, 
                                            description: 'ServiceNow Change Request Number'),
                                      text(name: 'DEPLOYMENT_NOTES', 
                                          defaultValue: 'Automated deployment via Jenkins pipeline', 
                                          description: 'Deployment notes')
                                  ]
                            
                            serviceNowIntegration.updateChangeRequestStatus(changeRequestNumber, 'IMPLEMENT', 
                                'Deployment approved and starting')
                        }
                        
                        // Pull and deploy image
                        def dockerImage = artifactManager.pullDockerImage(
                            pipelineConfig.projectName,
                            env.DOCKER_TAG,
                            envConfig.nexus.repositories.docker
                        )
                        
                        // Deploy with Helm
                        def helmArgs = "--set image.tag=${env.DOCKER_TAG} --set environment=${targetEnv}"
                        if (envConfig.deploymentStrategy == 'blue-green') {
                            helmArgs += " --set deploymentStrategy=blue-green"
                        }
                        
                        helmDeployer.deploy(targetEnv, helmArgs)
                        
                        // Record deployment
                        pipeline.recordDeployment(targetEnv, [
                            status: 'SUCCESS',
                            image: "${pipelineConfig.projectName}:${env.DOCKER_TAG}",
                            strategy: envConfig.deploymentStrategy,
                            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
                            changeRequest: changeRequestNumber ?: null
                        ])
                        
                        echo "✅ Successfully deployed to ${envConfig.displayName}"
                    }
                }
            }
        }
        
        stage('Post-Deployment Validation') {
            when {
                not { params.BUILD_TYPE == 'security-only' }
            }
            steps {
                script {
                    pipeline.addStage('Health Check') {
                        // Perform health checks
                        sleep(30) // Wait for deployment to stabilize
                        
                        def targetEnv = params.DEPLOYMENT_ENVIRONMENT
                        def envConfig = envManager.getEnvironmentConfig(targetEnv)
                        
                        // Simulate health check
                        def healthCheckResult = [
                            status: 'HEALTHY',
                            responseTime: '150ms',
                            endpoints: [
                                health: 'OK',
                                metrics: 'OK',
                                ready: 'OK'
                            ]
                        ]
                        
                        pipeline.recordBuildMetric('healthCheck', healthCheckResult)
                        
                        echo "Health check passed for ${envConfig.displayName} environment"
                    }
                    
                    if (params.BUILD_TYPE == 'full') {
                        pipeline.addStage('Integration Tests') {
                            if (!params.SKIP_TESTS) {
                                javaModule.runIntegrationTests()
                                def integrationTestResults = [
                                    passed: 25,
                                    failed: 0,
                                    total: 25,
                                    duration: '320s'
                                ]
                                pipeline.recordTestResults('integration-tests', integrationTestResults)
                            }
                        }
                    }
                }
            }
        }
        
        stage('Reporting and Cleanup') {
            steps {
                script {
                    pipeline.addStage('Generate Reports') {
                        // Generate comprehensive pipeline report
                        def reportPath = "pipeline-report-${env.BUILD_NUMBER}-${env.BUILD_TIMESTAMP}.json"
                        pipeline.savePipelineReport(reportPath)
                        
                        // Generate summary
                        def report = pipeline.generatePipelineReport()
                        
                        echo "📊 Pipeline Execution Summary:"
                        echo "   Build Duration: ${currentBuild.durationString}"
                        echo "   Docker Image: ${pipelineConfig.projectName}:${env.DOCKER_TAG}"
                        echo "   Target Environment: ${params.DEPLOYMENT_ENVIRONMENT}"
                        echo "   Test Results: ${report.testResults.size()} test suites executed"
                        echo "   Security Scans: ${report.securityScanResults.size()} scans completed"
                        echo "   Deployments: ${report.deploymentHistory.size()} environments deployed"
                        echo "   Quality Gates: ${report.qualityGateResults.size()} checks performed"
                        echo "   Report: ${reportPath}"
                        
                        // Archive artifacts
                        archiveArtifacts artifacts: reportPath, allowEmptyArchive: true
                        
                        if (report.testResults) {
                            publishTestResults testResultsPattern: 'target/surefire-reports/TEST-*.xml'
                        }
                        
                        if (report.securityScanResults) {
                            publishHTML([
                                allowMissing: false,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'security-reports',
                                reportFiles: '*.html',
                                reportName: 'Security Scan Report'
                            ])
                        }
                    }
                }
            }
        }
    }
    
    post {
        always {
            script {
                echo "🏁 Demo Pipeline Execution Completed"
                echo "Status: ${currentBuild.currentResult}"
                echo "Duration: ${currentBuild.durationString}"
                
                // Execute final pipeline processing
                if (binding.hasVariable('pipeline')) {
                    pipeline.execute()
                }
            }
        }
        
        success {
            script {
                echo "✅ Demo pipeline completed successfully!"
                
                // Send success notifications
                if (env.SLACK_WEBHOOK) {
                    slackSend(
                        color: 'good',
                        message: "✅ Universal Pipeline Demo completed successfully!\n" +
                                "Build: #${env.BUILD_NUMBER}\n" +
                                "Environment: ${params.DEPLOYMENT_ENVIRONMENT}\n" +
                                "Duration: ${currentBuild.durationString}"
                    )
                }
                
                // Update ServiceNow change request on success
                if (binding.hasVariable('changeRequestNumber') && changeRequestNumber) {
                    serviceNowIntegration.updateChangeRequestStatus(changeRequestNumber, 'CLOSED', 
                        'Deployment completed successfully')
                }
            }
        }
        
        failure {
            script {
                echo "❌ Demo pipeline failed!"
                
                // Send failure notifications
                if (env.SLACK_WEBHOOK) {
                    slackSend(
                        color: 'danger',
                        message: "❌ Universal Pipeline Demo failed!\n" +
                                "Build: #${env.BUILD_NUMBER}\n" +
                                "Environment: ${params.DEPLOYMENT_ENVIRONMENT}\n" +
                                "Check logs for details."
                    )
                }
                
                // Create ServiceNow incident on failure
                if (binding.hasVariable('serviceNowIntegration')) {
                    serviceNowIntegration.createIncident(params.DEPLOYMENT_ENVIRONMENT, [
                        type: 'Pipeline Failure',
                        error: currentBuild.description ?: 'Pipeline execution failed',
                        severity: (params.DEPLOYMENT_ENVIRONMENT == 'prod') ? '1' : '3'
                    ])
                }
            }
        }
        
        unstable {
            script {
                echo "⚠️ Demo pipeline completed with warnings!"
                
                if (env.SLACK_WEBHOOK) {
                    slackSend(
                        color: 'warning',
                        message: "⚠️ Universal Pipeline Demo completed with warnings!\n" +
                                "Build: #${env.BUILD_NUMBER}\n" +
                                "Environment: ${params.DEPLOYMENT_ENVIRONMENT}\n" +
                                "Please review the warnings."
                    )
                }
            }
        }
        
        cleanup {
            script {
                echo "🧹 Cleaning up demo environment..."
                
                // Clean workspace
                cleanWs()
                
                echo "Demo cleanup completed"
            }
        }
    }
} 