package modules

/**
 * 基础流水线框架，提供模块化构建、测试和部署的核心功能
 */
class BasePipeline {
    def script
    def config
    def stages = []
    def parallelStages = [:]
    def qualityGateResults = [:]
    def currentEnvironment = 'dev'
    def buildMetrics = [:]
    def artifactRegistry = [:]
    def testResults = [:]
    def securityScanResults = [:]
    def deploymentHistory = []

    /**
     * 初始化流水线
     * @param script Jenkinsfile脚本上下文
     * @param config 流水线配置
     */
    BasePipeline(script, config) {
        this.script = script
        this.config = config ?: [:]
        this.buildMetrics = initializeBuildMetrics()
        script.echo "初始化基础流水线，配置: ${config}"
    }

    /**
     * 初始化构建指标收集
     */
    private Map initializeBuildMetrics() {
        return [
            buildNumber: script.env.BUILD_NUMBER,
            buildId: script.env.BUILD_ID,
            jobName: script.env.JOB_NAME,
            gitBranch: script.env.GIT_BRANCH ?: 'unknown',
            gitCommit: script.env.GIT_COMMIT ?: 'unknown',
            startTime: new Date().format('yyyy-MM-dd HH:mm:ss'),
            buildTrigger: script.env.BUILD_CAUSE ?: 'manual',
            buildDuration: 0,
            status: 'RUNNING'
        ]
    }

    /**
     * 获取环境特定配置
     * @param environment 环境名称
     * @return 环境配置
     */
    Map getEnvironmentConfig(String environment) {
        def envConfig = config.environments?.find { it.name == environment }
        if (!envConfig) {
            script.error "未找到环境 ${environment} 的配置"
        }
        return envConfig
    }

    /**
     * 获取环境特定的Nexus配置
     * @param environment 环境名称
     * @return Nexus配置
     */
    Map getNexusConfigForEnvironment(String environment) {
        def envConfig = getEnvironmentConfig(environment)
        return envConfig.nexus ?: config.nexus
    }

    /**
     * 添加串行执行的阶段
     * @param name 阶段名称
     * @param action 阶段执行的闭包
     */
    void addStage(String name, Closure action) {
        stages << [name: name, action: action]
        script.echo "添加串行阶段: ${name}"
    }

    /**
     * 添加并行执行的阶段
     * @param name 阶段名称
     * @param action 阶段执行的闭包
     */
    void addParallelStage(String name, Closure action) {
        parallelStages[name] = action
        script.echo "添加并行阶段: ${name}"
    }

    /**
     * 执行所有已添加的阶段
     */
    void execute() {
        script.echo "开始执行流水线，共${stages.size()}个串行阶段和${parallelStages.size()}个并行阶段"

        // 执行串行阶段
        stages.each { stage ->
            script.stage(stage.name) {
                script.echo "执行阶段: ${stage.name}"
                stage.action.call()
            }
        }

        // 执行并行阶段
        if (parallelStages) {
            script.stage('并行执行阶段') {
                script.parallel(parallelStages)
            }
        }

        // 执行质量门禁检查
        if (config.qualityGateEnabled != false) {
            executeQualityGate()
        }
    }

    /**
     * 执行质量门禁检查
     */
    void executeQualityGate() {
        script.stage('质量门禁检查') {
            script.echo "执行质量门禁检查..."
            boolean qualityGatePassed = true

            // 检查所有质量指标
            qualityGateResults.each { key, value ->
                script.echo "${key}: ${value.result}"
                if (!value.passed) {
                    qualityGatePassed = false
                }
            }

            if (!qualityGatePassed) {
                script.error "质量门禁检查失败，请检查报告并修复问题"
            }
            script.echo "质量门禁检查通过"
        }
    }

    /**
     * 记录质量检查结果
     * @param name 检查名称
     * @param passed 是否通过
     * @param result 检查结果详情
     */
    void recordQualityResult(String name, boolean passed, def result) {
        qualityGateResults[name] = [passed: passed, result: result]
    }

    /**
     * 部署到指定环境
     * @param environment 环境名称
     * @param deployAction 部署执行的闭包
     */
    void deployTo(String environment, Closure deployAction) {
        currentEnvironment = environment
        script.stage("部署到${environment}环境") {
            script.echo "开始部署到${environment}环境..."
            deployAction.call()
            script.echo "成功部署到${environment}环境"
        }
    }

    /**
     * 生成部署工单
     * @param environment 目标环境
     * @param details 工单详情
     */
    void generateDeploymentTicket(String environment, def details) {
        script.stage("生成${environment}部署工单") {
            script.echo "生成部署工单: ${details}"
            // 实际实现中可以集成JIRA或其他工单系统API
            def ticketId = "DEPLOY-${System.currentTimeMillis()}"
            script.echo "成功生成${environment}环境部署工单: ${ticketId}"
            return ticketId
        }
    }

    /**
     * 记录构建指标
     * @param key 指标键
     * @param value 指标值
     */
    void recordBuildMetric(String key, def value) {
        buildMetrics[key] = value
        script.echo "记录构建指标: ${key} = ${value}"
    }

    /**
     * 记录测试结果
     * @param testType 测试类型
     * @param results 测试结果
     */
    void recordTestResults(String testType, Map results) {
        testResults[testType] = results
        script.echo "记录测试结果: ${testType} - 通过: ${results.passed}, 失败: ${results.failed}"
    }

    /**
     * 记录安全扫描结果
     * @param scanType 扫描类型
     * @param results 扫描结果
     */
    void recordSecurityScanResults(String scanType, Map results) {
        securityScanResults[scanType] = results
        script.echo "记录安全扫描结果: ${scanType} - ${results.status}"
    }

    /**
     * 注册制品信息
     * @param artifactType 制品类型
     * @param artifactDetails 制品详情
     */
    void registerArtifact(String artifactType, Map artifactDetails) {
        if (!artifactRegistry[artifactType]) {
            artifactRegistry[artifactType] = []
        }
        artifactRegistry[artifactType] << artifactDetails
        script.echo "注册制品: ${artifactType} - ${artifactDetails.name}:${artifactDetails.version}"
    }

    /**
     * 记录部署历史
     * @param environment 部署环境
     * @param deploymentDetails 部署详情
     */
    void recordDeployment(String environment, Map deploymentDetails) {
        deploymentHistory << [
            environment: environment,
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
            details: deploymentDetails
        ]
        script.echo "记录部署历史: ${environment} - ${deploymentDetails.status}"
    }

    /**
     * 生成流水线执行报告
     */
    Map generatePipelineReport() {
        buildMetrics.endTime = new Date().format('yyyy-MM-dd HH:mm:ss')
        buildMetrics.status = 'COMPLETED'
        
        return [
            buildMetrics: buildMetrics,
            qualityGateResults: qualityGateResults,
            testResults: testResults,
            securityScanResults: securityScanResults,
            artifactRegistry: artifactRegistry,
            deploymentHistory: deploymentHistory,
            generatedAt: new Date().format('yyyy-MM-dd HH:mm:ss')
        ]
    }

    /**
     * 保存流水线报告到文件
     * @param reportPath 报告保存路径
     */
    void savePipelineReport(String reportPath = 'pipeline-report.json') {
        def report = generatePipelineReport()
        script.writeJSON file: reportPath, json: report, pretty: 4
        script.echo "流水线报告已保存到: ${reportPath}"
        
        // 归档报告文件
        script.archiveArtifacts artifacts: reportPath, allowEmptyArchive: true
    }

    /**
     * 提交ServiceNow工单
     * @param environment 目标环境
     * @param details 工单详情
     * @return 工单ID
     */
    String submitServiceNowTicket(String environment, Map details) {
        script.stage("提交ServiceNow工单") {
            script.echo "向ServiceNow提交${environment}环境部署工单..."
            
            def pipelineReport = generatePipelineReport()
            def ticketPayload = [
                short_description: "部署请求: ${config.projectName} 到 ${environment} 环境",
                description: buildServiceNowDescription(environment, details, pipelineReport),
                category: "部署",
                subcategory: "应用部署",
                priority: environment == 'prod' ? '2' : '3',
                assignment_group: config.serviceNow?.assignmentGroup ?: 'DevOps Team',
                cmdb_ci: config.projectName,
                work_notes: "自动化流水线生成的部署工单",
                u_environment: environment,
                u_application: config.projectName,
                u_build_number: script.env.BUILD_NUMBER,
                u_git_commit: buildMetrics.gitCommit,
                u_pipeline_report: script.writeJSON returnText: true, json: pipelineReport
            ]
            
            def serviceNowUrl = config.serviceNow?.url ?: script.error("未配置ServiceNow URL")
            def username = config.serviceNow?.username ?: script.error("未配置ServiceNow用户名")
            def password = config.serviceNow?.password ?: script.error("未配置ServiceNow密码")
            
            // 调用ServiceNow REST API
            def response = script.httpRequest(
                acceptType: 'APPLICATION_JSON',
                contentType: 'APPLICATION_JSON',
                httpMode: 'POST',
                requestBody: script.writeJSON(returnText: true, json: ticketPayload),
                url: "${serviceNowUrl}/api/now/table/incident",
                authentication: script.usernamePassword(credentialsId: 'servicenow-credentials')
            )
            
            def responseJson = script.readJSON text: response.content
            def ticketNumber = responseJson.result.number
            
            script.echo "ServiceNow工单创建成功: ${ticketNumber}"
            return ticketNumber
        }
    }

    /**
     * 构建ServiceNow工单描述
     */
    private String buildServiceNowDescription(String environment, Map details, Map pipelineReport) {
        def description = """
部署申请详情:
- 应用名称: ${config.projectName}
- 目标环境: ${environment}
- 构建编号: ${script.env.BUILD_NUMBER}
- Git分支: ${buildMetrics.gitBranch}
- Git提交: ${buildMetrics.gitCommit}
- 申请人: ${script.env.BUILD_USER ?: 'Jenkins'}

质量检查结果:
${qualityGateResults.collect { k, v -> "- ${k}: ${v.passed ? '通过' : '失败'}" }.join('\n')}

测试结果概要:
${testResults.collect { k, v -> "- ${k}: 通过 ${v.passed ?: 0}, 失败 ${v.failed ?: 0}" }.join('\n')}

安全扫描结果:
${securityScanResults.collect { k, v -> "- ${k}: ${v.status}" }.join('\n')}

制品信息:
${artifactRegistry.collect { k, v -> "- ${k}: ${v.size()} 个制品" }.join('\n')}

详细报告请查看Jenkins构建页面。
        """
        return description.trim()
    }
}