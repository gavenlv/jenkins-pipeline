/**
 * Multi-Branch Pipeline Example
 * Demonstrates how to use the framework with different Git branches:
 * - master/main: Full pipeline with all environments
 * - develop: Development pipeline with dev/sit environments
 * - feature/*: Feature branch with ephemeral environments
 * - release/*: Release candidate pipeline
 * - hotfix/*: Fast-track hotfix pipeline
 */
@Library('pipeline-library') _

// Import required modules
import modules.BasePipeline
import modules.EnvironmentConfigManager
import modules.GitBranchManager
import modules.languages.JavaModule
import modules.quality.SecurityScanModule
import modules.deployment.HelmDeploymentModule
import modules.artifacts.ArtifactManager
import modules.integration.ServiceNowIntegration

pipeline {
    agent any
    
    parameters {
        choice(
            name: 'BUILD_BRANCH',
            choices: ['auto', 'master', 'develop', 'feature/user-authentication', 'release/1.0.0', 'hotfix/critical-fix'],
            description: 'Select branch to build (auto detects current branch)'
        )
        choice(
            name: 'TARGET_ENVIRONMENT',
            choices: ['auto', 'dev', 'sit', 'uat', 'prod'],
            description: 'Target environment (auto selects based on branch)'
        )
        booleanParam(
            name: 'SKIP_TESTS',
            defaultValue: false,
            description: 'Skip running tests (not recommended for production branches)'
        )
        booleanParam(
            name: 'FORCE_DEPLOY',
            defaultValue: false,
            description: 'Force deployment even if quality gates fail'
        )
        booleanParam(
            name: 'CREATE_EPHEMERAL_ENV',
            defaultValue: true,
            description: 'Create ephemeral environment for feature branches'
        )
    }
    
    stages {
        stage('Initialize Pipeline') {
            steps {
                script {
                    // Define pipeline configuration
                    def pipelineConfig = [
                        projectName: 'multi-branch-app',
                        buildTool: 'maven',
                        sonarProjectKey: 'com.example:multi-branch-app',
                        git: [
                            url: 'https://github.com/example/multi-branch-app.git',
                            credentialsId: 'github-credentials'
                        ],
                        branchConfigs: [
                            // Custom branch configurations can be added here
                            'feature/special-*': [
                                environments: ['dev', 'sit'],
                                autoPromote: ['dev'],
                                requiresApproval: ['sit'],
                                deploymentStrategy: 'canary',
                                qualityGateRequired: true,
                                securityScanRequired: true,
                                performanceTestRequired: false,
                                ephemeralEnvironment: true
                            ]
                        ],
                        nexus: [
                            url: 'https://nexus.example.com',
                            user: 'admin',
                            password: '${NEXUS_PASSWORD}',
                            repositories: [
                                maven: 'maven-releases',
                                docker: 'docker-releases',
                                generic: 'generic-releases'
                            ]
                        ],
                        helm: [
                            chartPath: 'charts/multi-branch-app',
                            releaseName: 'multi-branch-app',
                            namespace: 'applications',
                            valuesFiles: [
                                common: 'charts/multi-branch-app/values.yaml',
                                dev: 'charts/multi-branch-app/values-dev.yaml',
                                sit: 'charts/multi-branch-app/values-sit.yaml',
                                uat: 'charts/multi-branch-app/values-uat.yaml',
                                prod: 'charts/multi-branch-app/values-prod.yaml'
                            ]
                        ],
                        serviceNow: [
                            url: 'https://company.service-now.com',
                            assignmentGroup: 'DevOps Team'
                        ]
                    ]
                    
                    // Initialize base pipeline
                    pipeline = new BasePipeline(this, pipelineConfig)
                    
                    // Initialize Git branch manager
                    gitManager = new GitBranchManager(pipeline, pipelineConfig)
                    
                    // Initialize environment manager
                    envManager = new EnvironmentConfigManager(pipeline, pipelineConfig)
                    
                    // Initialize specialized modules
                    javaModule = new JavaModule(pipeline, [buildTool: pipelineConfig.buildTool])
                    securityScanner = new SecurityScanModule(pipeline)
                    helmDeployer = new HelmDeploymentModule(pipeline, pipelineConfig.helm)
                    artifactManager = new ArtifactManager(pipeline, [
                        nexusUrl: pipelineConfig.nexus.url,
                        nexusUser: pipelineConfig.nexus.user,
                        nexusPassword: pipelineConfig.nexus.password,
                        repositories: pipelineConfig.nexus.repositories
                    ])
                    serviceNowIntegration = new ServiceNowIntegration(pipeline, pipelineConfig.serviceNow)
                    
                    echo "Pipeline modules initialized successfully"
                }
            }
        }
        
        stage('Git Checkout') {
            steps {
                script {
                    def targetBranch = params.BUILD_BRANCH == 'auto' ? env.BRANCH_NAME : params.BUILD_BRANCH
                    
                    if (targetBranch && targetBranch != 'auto') {
                        // Clone specific branch
                        branchInfo = gitManager.cloneRepository(
                            pipelineConfig.git.url,
                            targetBranch,
                            pipelineConfig.git.credentialsId
                        )
                    } else {
                        // Use default checkout
                        checkout scm
                        branchInfo = gitManager.getCurrentBranchInfo()
                    }
                    
                    // Get branch-specific build configuration
                    buildConfig = gitManager.getBuildConfiguration()
                    
                    echo "=== Branch Information ==="
                    echo "Branch: ${branchInfo.name} (${branchInfo.type})"
                    echo "Commit: ${branchInfo.shortCommit}"
                    echo "Author: ${branchInfo.author}"
                    echo "Docker Tag: ${buildConfig.dockerTag}"
                    echo "Allowed Environments: ${buildConfig.allowedEnvironments.join(', ')}"
                    echo "Deployment Strategy: ${buildConfig.deploymentStrategy}"
                    
                    if (buildConfig.ephemeralEnv) {
                        echo "Ephemeral Environment: ${buildConfig.ephemeralEnv}"
                    }
                }
            }
        }
        
        stage('Build and Test') {
            parallel {
                stage('Build Application') {
                    steps {
                        script {
                            pipeline.addStage('Build') {
                                javaModule.build()
                            }
                            pipeline.executeStage('Build')
                        }
                    }
                }
                
                stage('Unit Tests') {
                    when {
                        not { params.SKIP_TESTS }
                    }
                    steps {
                        script {
                            pipeline.addStage('Unit Tests') {
                                javaModule.runUnitTests()
                                javaModule.generateCoverageReport()
                            }
                            pipeline.executeStage('Unit Tests')
                        }
                    }
                }
            }
        }
        
        stage('Code Quality Analysis') {
            parallel {
                stage('SonarQube Analysis') {
                    when {
                        expression { buildConfig.qualityGateRequired }
                    }
                    steps {
                        script {
                            pipeline.addStage('SonarQube') {
                                javaModule.runSonarQubeScan(pipelineConfig.sonarProjectKey)
                            }
                            pipeline.executeStage('SonarQube')
                        }
                    }
                }
                
                stage('Security Scanning') {
                    when {
                        expression { buildConfig.securityScanRequired }
                    }
                    steps {
                        script {
                            pipeline.addStage('Security Scan') {
                                securityScanner.runSecretScan()
                                securityScanner.runOwaspDependencyCheck('maven')
                            }
                            pipeline.executeStage('Security Scan')
                        }
                    }
                }
            }
        }
        
        stage('Docker Build') {
            steps {
                script {
                    pipeline.addStage('Docker Build') {
                        dockerImage = docker.build("${pipelineConfig.projectName}:${buildConfig.dockerTag}")
                        
                        if (buildConfig.securityScanRequired) {
                            securityScanner.scanDockerImage(pipelineConfig.projectName, buildConfig.dockerTag)
                        }
                        
                        artifactManager.pushDockerImage(
                            pipelineConfig.projectName,
                            buildConfig.dockerTag,
                            pipelineConfig.nexus.repositories.docker
                        )
                    }
                    pipeline.executeStage('Docker Build')
                }
            }
        }
        
        stage('Deploy to Environments') {
            steps {
                script {
                    def targetEnv = params.TARGET_ENVIRONMENT == 'auto' ? null : params.TARGET_ENVIRONMENT
                    def allowedEnvironments = buildConfig.allowedEnvironments
                    
                    if (targetEnv && !allowedEnvironments.contains(targetEnv)) {
                        error("Branch ${branchInfo.name} is not allowed to deploy to ${targetEnv}. " +
                              "Allowed environments: ${allowedEnvironments.join(', ')}")
                    }
                    
                    def envsToDeploy = targetEnv ? [targetEnv] : allowedEnvironments
                    
                    for (environment in envsToDeploy) {
                        deployToEnvironment(environment)
                    }
                }
            }
        }
        
        stage('Integration Tests') {
            when {
                expression { 
                    buildConfig.allowedEnvironments.contains('sit') && 
                    !params.SKIP_TESTS 
                }
            }
            steps {
                script {
                    pipeline.addStage('Integration Tests') {
                        javaModule.runIntegrationTests('-Pintegration-test')
                    }
                    pipeline.executeStage('Integration Tests')
                }
            }
        }
        
        stage('Performance Tests') {
            when {
                expression { 
                    buildConfig.performanceTestRequired && 
                    !params.SKIP_TESTS 
                }
            }
            steps {
                script {
                    pipeline.addStage('Performance Tests') {
                        // Add performance testing logic here
                        echo "Running performance tests..."
                        sh 'echo "Performance tests would run here"'
                    }
                    pipeline.executeStage('Performance Tests')
                }
            }
        }
        
        stage('Cleanup Ephemeral Environment') {
            when {
                expression { 
                    buildConfig.ephemeralEnv && 
                    !params.CREATE_EPHEMERAL_ENV 
                }
            }
            steps {
                script {
                    pipeline.addStage('Cleanup') {
                        echo "Cleaning up ephemeral environment: ${buildConfig.ephemeralEnv}"
                        helmDeployer.uninstall(buildConfig.ephemeralEnv)
                    }
                    pipeline.executeStage('Cleanup')
                }
            }
        }
    }
    
    post {
        always {
            script {
                // Generate pipeline report
                def report = pipeline.generatePipelineReport()
                def branchReport = gitManager.generateBranchReport()
                
                // Save reports
                writeJSON file: "pipeline-report-${env.BUILD_NUMBER}.json", json: report
                writeJSON file: "branch-report-${env.BUILD_NUMBER}.json", json: branchReport
                
                // Archive reports
                archiveArtifacts artifacts: '*-report-*.json', allowEmptyArchive: true
                
                echo "=== Pipeline Summary ==="
                echo "Branch: ${branchInfo.name} (${branchInfo.type})"
                echo "Build Status: ${currentBuild.result ?: 'SUCCESS'}"
                echo "Duration: ${currentBuild.durationString}"
                echo "Docker Tag: ${buildConfig.dockerTag}"
                
                if (report.deploymentHistory) {
                    echo "Deployed to: ${report.deploymentHistory.collect { it.environment }.join(', ')}"
                }
            }
        }
        
        success {
            script {
                if (buildConfig.fastTrack) {
                    echo "🚀 Fast-track deployment completed successfully!"
                } else {
                    echo "✅ Pipeline completed successfully!"
                }
            }
        }
        
        failure {
            script {
                echo "❌ Pipeline failed!"
                
                if (branchInfo.type == 'hotfix') {
                    // Create incident for failed hotfix
                    serviceNowIntegration.createIncident([
                        title: "Hotfix deployment failed: ${branchInfo.name}",
                        description: "Pipeline failed for hotfix branch ${branchInfo.name}",
                        urgency: 'High',
                        assignmentGroup: pipelineConfig.serviceNow.assignmentGroup
                    ])
                }
            }
        }
    }
}

/**
 * Deploy to specific environment with branch validation
 */
def deployToEnvironment(environment) {
    script {
        // Validate branch for environment
        gitManager.validateBranchForEnvironment(environment)
        
        def stageName = "Deploy to ${environment.toUpperCase()}"
        
        pipeline.addStage(stageName) {
            def envConfig = envManager.getEnvironmentConfig(environment)
            
            // Handle approvals
            if (gitManager.requiresApproval(environment) && !params.FORCE_DEPLOY) {
                def approvalMessage = "Approve deployment of ${branchInfo.name} to ${environment}?"
                
                if (environment == 'prod' || gitManager.isFastTrack()) {
                    // Create ServiceNow change request for production or hotfix
                    def changeRequestNumber = serviceNowIntegration.createChangeRequest(environment, [
                        strategy: buildConfig.deploymentStrategy,
                        requester: env.BUILD_USER ?: 'Jenkins',
                        branch: branchInfo.name,
                        commit: branchInfo.shortCommit
                    ])
                    
                    input message: approvalMessage,
                          ok: 'Approve',
                          parameters: [
                              string(name: 'CHANGE_REQUEST', 
                                     defaultValue: changeRequestNumber, 
                                     description: 'ServiceNow Change Request Number'),
                              booleanParam(name: 'EMERGENCY_DEPLOYMENT', 
                                          defaultValue: gitManager.isFastTrack(), 
                                          description: 'Is this an emergency deployment?')
                          ]
                    
                    serviceNowIntegration.updateChangeRequestStatus(
                        changeRequestNumber, 
                        'IMPLEMENT', 
                        "Deployment approved for ${environment}"
                    )
                } else {
                    input message: approvalMessage, ok: 'Approve'
                }
            }
            
            // Handle ephemeral environments for feature branches
            def targetNamespace = envConfig.namespace
            def releaseName = pipelineConfig.helm.releaseName
            
            if (buildConfig.ephemeralEnv && params.CREATE_EPHEMERAL_ENV) {
                targetNamespace = "ephemeral-${buildConfig.ephemeralEnv}"
                releaseName = buildConfig.ephemeralEnv
                
                echo "Creating ephemeral environment: ${targetNamespace}"
            }
            
            // Deploy using Helm
            def dockerImage = "${pipelineConfig.projectName}:${buildConfig.dockerTag}"
            def helmArgs = "--set image.repository=${pipelineConfig.projectName} " +
                          "--set image.tag=${buildConfig.dockerTag} " +
                          "--set deploymentStrategy=${buildConfig.deploymentStrategy} " +
                          "--namespace ${targetNamespace}"
            
            if (buildConfig.ephemeralEnv) {
                helmArgs += " --set ephemeral=true --set branch=${branchInfo.name}"
            }
            
            helmDeployer.deploy(environment, helmArgs, releaseName, targetNamespace)
            
            // Record deployment
            pipeline.recordDeployment(environment, [
                status: 'SUCCESS',
                image: dockerImage,
                strategy: buildConfig.deploymentStrategy,
                branch: branchInfo.name,
                commit: branchInfo.shortCommit,
                ephemeral: buildConfig.ephemeralEnv != null,
                namespace: targetNamespace
            ])
            
            echo "✅ Successfully deployed ${branchInfo.name} to ${environment}"
        }
        
        pipeline.executeStage(stageName)
    }
} 