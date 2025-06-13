package modules.languages

import modules.BasePipeline

/**
 * Python语言专用CI模块，提供依赖管理、测试执行和质量检查功能
 */
class PythonModule {
    private BasePipeline pipeline
    private String pythonVersion = '3.9'
    private String testTool = 'pytest'
    private String testReportPath = 'test-reports'
    private String coverageTool = 'coverage'

    /**
     * 初始化Python模块
     * @param pipeline 基础流水线实例
     * @param config 语言特定配置
     */
    PythonModule(BasePipeline pipeline, config = [:]) {
        this.pipeline = pipeline
        if (config.pythonVersion) this.pythonVersion = config.pythonVersion
        if (config.testTool) this.testTool = config.testTool
        if (config.testReportPath) this.testReportPath = config.testReportPath
        if (config.coverageTool) this.coverageTool = config.coverageTool
        pipeline.script.echo "初始化Python模块，Python版本: ${pythonVersion}"
    }

    /**
     * 安装Python依赖
     * @param requirementsFile requirements文件路径
     */
    void installDependencies(String requirementsFile = 'requirements.txt') {
        pipeline.script.stage("Python依赖安装") {
            pipeline.script.echo "安装Python依赖..."
            pipeline.script.sh "python -m pip install --upgrade pip"
            pipeline.script.sh "pip install -r ${requirementsFile}"
            pipeline.script.echo "Python依赖安装完成"
        }
    }

    /**
     * 运行单元测试
     * @param testDir 测试目录
     * @param args 测试参数
     */
    void runUnitTests(String testDir = 'tests', String args = '') {
        pipeline.script.stage("Python单元测试") {
            pipeline.script.echo "运行Python单元测试..."
            pipeline.script.sh "mkdir -p ${testReportPath}"
            def testCommand = "${testTool} ${testDir} ${args} --junitxml=${testReportPath}/unit-test-results.xml"
            pipeline.script.sh(testCommand)

            // 发布测试报告
            pipeline.script.junit "${testReportPath}/unit-test-results.xml"
            pipeline.script.echo "Python单元测试完成，报告已生成"
        }
    }

    /**
     * 运行集成测试
     * @param testDir 集成测试目录
     * @param args 测试参数
     */
    void runIntegrationTests(String testDir = 'integration-tests', String args = '') {
        pipeline.script.stage("Python集成测试") {
            pipeline.script.echo "运行Python集成测试..."
            pipeline.script.sh "mkdir -p ${testReportPath}"
            def testCommand = "${testTool} ${testDir} ${args} --junitxml=${testReportPath}/integration-test-results.xml"
            pipeline.script.sh(testCommand)

            // 发布集成测试报告
            pipeline.script.junit "${testReportPath}/integration-test-results.xml"
            pipeline.script.echo "Python集成测试完成，报告已生成"
        }
    }

    /**
     * 生成代码覆盖率报告
     * @param sourceDir 源代码目录
     * @param testDir 测试目录
     */
    void generateCoverageReport(String sourceDir = 'src', String testDir = 'tests') {
        pipeline.script.stage("Python代码覆盖率") {
            pipeline.script.echo "生成Python代码覆盖率报告..."
            pipeline.script.sh "${coverageTool} run --source=${sourceDir} -m ${testTool} ${testDir}"
            pipeline.script.sh "${coverageTool} xml -o ${testReportPath}/coverage.xml"
            pipeline.script.sh "${coverageTool} html -d ${testReportPath}/coverage"

            // 发布覆盖率报告
            pipeline.script.publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: "${testReportPath}/coverage",
                reportFiles: 'index.html',
                reportName: 'Python Code Coverage Report'
            ])
            pipeline.script.echo "Python代码覆盖率报告生成完成"
        }
    }

    /**
     * 执行SonarQube代码质量扫描
     * @param projectKey SonarQube项目键
     * @param args 扫描参数
     */
    void runSonarQubeScan(String projectKey, String args = '') {
        pipeline.script.stage("SonarQube扫描") {
            pipeline.script.echo "执行SonarQube代码质量扫描..."
            def sonarCommand = "sonar-scanner -Dsonar.projectKey=${projectKey} -Dsonar.sources=. -Dsonar.python.coverage.reportPaths=${testReportPath}/coverage.xml ${args}"
            pipeline.script.sh(sonarCommand)

            // 记录质量检查结果
            pipeline.recordQualityResult("SonarQube", true, [status: "passed", projectKey: projectKey])
            pipeline.script.echo "SonarQube扫描完成"
        }
    }
}