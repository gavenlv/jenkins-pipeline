package modules

/**
 * Git Branch Manager
 * Handles Git branch-specific build strategies and deployment logic
 */
class GitBranchManager {
    
    def script
    def pipeline
    def config
    
    // Branch patterns and their configurations
    def branchConfigs = [
        master: [
            environments: ['dev', 'sit', 'uat', 'prod'],
            autoPromote: ['dev', 'sit'],
            requiresApproval: ['uat', 'prod'],
            deploymentStrategy: 'blue-green',
            qualityGateRequired: true,
            securityScanRequired: true,
            performanceTestRequired: true
        ],
        main: [
            environments: ['dev', 'sit', 'uat', 'prod'],
            autoPromote: ['dev', 'sit'],
            requiresApproval: ['uat', 'prod'],
            deploymentStrategy: 'blue-green',
            qualityGateRequired: true,
            securityScanRequired: true,
            performanceTestRequired: true
        ],
        develop: [
            environments: ['dev', 'sit'],
            autoPromote: ['dev'],
            requiresApproval: ['sit'],
            deploymentStrategy: 'rolling-update',
            qualityGateRequired: true,
            securityScanRequired: true,
            performanceTestRequired: false
        ],
        'feature/*': [
            environments: ['dev'],
            autoPromote: ['dev'],
            requiresApproval: [],
            deploymentStrategy: 'rolling-update',
            qualityGateRequired: false,
            securityScanRequired: true,
            performanceTestRequired: false,
            ephemeralEnvironment: true
        ],
        'release/*': [
            environments: ['dev', 'sit', 'uat'],
            autoPromote: ['dev', 'sit'],
            requiresApproval: ['uat'],
            deploymentStrategy: 'blue-green',
            qualityGateRequired: true,
            securityScanRequired: true,
            performanceTestRequired: true
        ],
        'hotfix/*': [
            environments: ['dev', 'sit', 'uat', 'prod'],
            autoPromote: ['dev'],
            requiresApproval: ['sit', 'uat', 'prod'],
            deploymentStrategy: 'blue-green',
            qualityGateRequired: true,
            securityScanRequired: true,
            performanceTestRequired: true,
            fastTrack: true
        ]
    ]
    
    GitBranchManager(pipeline, config = [:]) {
        this.script = pipeline.script
        this.pipeline = pipeline
        this.config = config
        
        // Merge user-defined branch configurations
        if (config.branchConfigs) {
            this.branchConfigs.putAll(config.branchConfigs)
        }
        
        script.echo "GitBranchManager initialized"
    }
    
    /**
     * Get current Git branch information
     */
    def getCurrentBranchInfo() {
        def branchInfo = [:]
        
        try {
            // Get current branch name
            branchInfo.name = script.env.GIT_BRANCH ?: script.sh(
                script: 'git rev-parse --abbrev-ref HEAD',
                returnStdout: true
            ).trim()
            
            // Remove origin/ prefix if present
            if (branchInfo.name.startsWith('origin/')) {
                branchInfo.name = branchInfo.name.substring(7)
            }
            
            // Get commit information
            branchInfo.commit = script.env.GIT_COMMIT ?: script.sh(
                script: 'git rev-parse HEAD',
                returnStdout: true
            ).trim()
            
            branchInfo.shortCommit = branchInfo.commit.substring(0, 8)
            
            // Get commit message
            branchInfo.commitMessage = script.sh(
                script: 'git log -1 --pretty=%B',
                returnStdout: true
            ).trim()
            
            // Get author information
            branchInfo.author = script.sh(
                script: 'git log -1 --pretty=%an',
                returnStdout: true
            ).trim()
            
            branchInfo.authorEmail = script.sh(
                script: 'git log -1 --pretty=%ae',
                returnStdout: true
            ).trim()
            
            // Get timestamp
            branchInfo.timestamp = script.sh(
                script: 'git log -1 --pretty=%ct',
                returnStdout: true
            ).trim()
            
            // Determine branch type
            branchInfo.type = determineBranchType(branchInfo.name)
            
            // Get branch configuration
            branchInfo.config = getBranchConfig(branchInfo.name)
            
            script.echo "Current branch: ${branchInfo.name} (${branchInfo.type})"
            script.echo "Commit: ${branchInfo.shortCommit} by ${branchInfo.author}"
            
        } catch (Exception e) {
            script.echo "Warning: Could not retrieve Git information: ${e.message}"
            branchInfo = [
                name: 'unknown',
                commit: 'unknown',
                shortCommit: 'unknown',
                type: 'unknown',
                config: branchConfigs.master // Default fallback
            ]
        }
        
        return branchInfo
    }
    
    /**
     * Determine branch type based on branch name
     */
    def determineBranchType(branchName) {
        if (branchName == 'master' || branchName == 'main') {
            return 'main'
        } else if (branchName == 'develop' || branchName == 'dev') {
            return 'develop'
        } else if (branchName.startsWith('feature/')) {
            return 'feature'
        } else if (branchName.startsWith('release/')) {
            return 'release'
        } else if (branchName.startsWith('hotfix/')) {
            return 'hotfix'
        } else if (branchName.startsWith('bugfix/')) {
            return 'bugfix'
        } else {
            return 'custom'
        }
    }
    
    /**
     * Get branch-specific configuration
     */
    def getBranchConfig(branchName) {
        def branchType = determineBranchType(branchName)
        
        // Try exact match first
        if (branchConfigs[branchName]) {
            return branchConfigs[branchName]
        }
        
        // Try pattern match
        for (pattern in branchConfigs.keySet()) {
            if (pattern.contains('*')) {
                def regex = pattern.replace('*', '.*')
                if (branchName.matches(regex)) {
                    return branchConfigs[pattern]
                }
            }
        }
        
        // Try branch type match
        if (branchConfigs[branchType]) {
            return branchConfigs[branchType]
        }
        
        // Default fallback
        script.echo "Warning: No specific configuration found for branch ${branchName}, using master config"
        return branchConfigs.master
    }
    
    /**
     * Get allowed environments for current branch
     */
    def getAllowedEnvironments(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        return config.environments ?: ['dev']
    }
    
    /**
     * Check if environment requires approval for current branch
     */
    def requiresApproval(environment, branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        return config.requiresApproval?.contains(environment) ?: false
    }
    
    /**
     * Check if environment supports auto-promotion for current branch
     */
    def supportsAutoPromotion(environment, branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        return config.autoPromote?.contains(environment) ?: false
    }
    
    /**
     * Get deployment strategy for current branch
     */
    def getDeploymentStrategy(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        return config.deploymentStrategy ?: 'rolling-update'
    }
    
    /**
     * Check if quality gate is required for current branch
     */
    def isQualityGateRequired(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        return config.qualityGateRequired ?: false
    }
    
    /**
     * Check if security scan is required for current branch
     */
    def isSecurityScanRequired(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        return config.securityScanRequired ?: true
    }
    
    /**
     * Check if performance test is required for current branch
     */
    def isPerformanceTestRequired(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        return config.performanceTestRequired ?: false
    }
    
    /**
     * Check if branch supports ephemeral environments
     */
    def supportsEphemeralEnvironment(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        return config.ephemeralEnvironment ?: false
    }
    
    /**
     * Check if branch is fast-track (for hotfixes)
     */
    def isFastTrack(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        return config.fastTrack ?: false
    }
    
    /**
     * Generate branch-specific Docker tag
     */
    def generateDockerTag(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def buildNumber = script.env.BUILD_NUMBER ?: 'latest'
        def timestamp = new Date().format('yyyyMMdd-HHmmss')
        
        def tag = ""
        
        switch (branchInfo.type) {
            case 'main':
                tag = "${buildNumber}"
                break
            case 'develop':
                tag = "dev-${buildNumber}"
                break
            case 'feature':
                def featureName = branchInfo.name.replace('feature/', '').replaceAll('[^a-zA-Z0-9]', '-')
                tag = "feature-${featureName}-${buildNumber}"
                break
            case 'release':
                def version = branchInfo.name.replace('release/', '')
                tag = "rc-${version}-${buildNumber}"
                break
            case 'hotfix':
                def version = branchInfo.name.replace('hotfix/', '')
                tag = "hotfix-${version}-${buildNumber}"
                break
            default:
                def safeBranchName = branchInfo.name.replaceAll('[^a-zA-Z0-9]', '-')
                tag = "${safeBranchName}-${buildNumber}"
        }
        
        return tag.toLowerCase()
    }
    
    /**
     * Generate ephemeral environment name for feature branches
     */
    def generateEphemeralEnvironmentName(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        
        if (!supportsEphemeralEnvironment(branchInfo.name)) {
            return null
        }
        
        def featureName = branchInfo.name.replace('feature/', '').replaceAll('[^a-zA-Z0-9]', '-')
        def shortCommit = branchInfo.shortCommit ?: 'unknown'
        
        return "feature-${featureName}-${shortCommit}".toLowerCase()
    }
    
    /**
     * Clone repository with specific branch
     */
    def cloneRepository(repoUrl, branchName, credentialsId = null) {
        script.echo "Cloning repository: ${repoUrl}, branch: ${branchName}"
        
        def checkoutConfig = [
            $class: 'GitSCM',
            branches: [[name: "*/${branchName}"]],
            userRemoteConfigs: [[url: repoUrl]]
        ]
        
        if (credentialsId) {
            checkoutConfig.userRemoteConfigs[0].credentialsId = credentialsId
        }
        
        script.checkout(checkoutConfig)
        
        // Record branch information
        def branchInfo = getCurrentBranchInfo()
        pipeline.recordBuildMetric('gitBranch', branchInfo.name)
        pipeline.recordBuildMetric('gitCommit', branchInfo.commit)
        pipeline.recordBuildMetric('gitAuthor', branchInfo.author)
        pipeline.recordBuildMetric('branchType', branchInfo.type)
        
        return branchInfo
    }
    
    /**
     * Validate branch for deployment to specific environment
     */
    def validateBranchForEnvironment(environment, branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def allowedEnvironments = getAllowedEnvironments(branchInfo.name)
        
        if (!allowedEnvironments.contains(environment)) {
            script.error("Branch ${branchInfo.name} is not allowed to deploy to ${environment}. " +
                        "Allowed environments: ${allowedEnvironments.join(', ')}")
        }
        
        script.echo "Branch ${branchInfo.name} validated for deployment to ${environment}"
        return true
    }
    
    /**
     * Get branch-specific build configuration
     */
    def getBuildConfiguration(branchName = null) {
        def branchInfo = branchName ? [name: branchName] : getCurrentBranchInfo()
        def config = getBranchConfig(branchInfo.name)
        
        return [
            branchName: branchInfo.name,
            branchType: branchInfo.type,
            dockerTag: generateDockerTag(branchInfo.name),
            ephemeralEnv: generateEphemeralEnvironmentName(branchInfo.name),
            allowedEnvironments: getAllowedEnvironments(branchInfo.name),
            deploymentStrategy: getDeploymentStrategy(branchInfo.name),
            qualityGateRequired: isQualityGateRequired(branchInfo.name),
            securityScanRequired: isSecurityScanRequired(branchInfo.name),
            performanceTestRequired: isPerformanceTestRequired(branchInfo.name),
            fastTrack: isFastTrack(branchInfo.name)
        ]
    }
    
    /**
     * Generate branch-specific pipeline report
     */
    def generateBranchReport() {
        def branchInfo = getCurrentBranchInfo()
        def buildConfig = getBuildConfiguration()
        
        return [
            branch: branchInfo,
            buildConfiguration: buildConfig,
            generatedAt: new Date().toString()
        ]
    }
} 