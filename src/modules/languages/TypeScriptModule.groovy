package modules.languages

import modules.BasePipeline

/**
 * TypeScript/ES6语言专用CI模块，提供前端项目的构建、测试和质量检查功能
 */
class TypeScriptModule {
    private BasePipeline pipeline
    private String packageManager = 'npm' // 默认包管理器为npm
    private String testTool = 'jest'
    private String testReportPath = 'test-reports'
    private String coverageTool = 'nyc'
    private String buildCommand = 'build'

    /**
     * 初始化TypeScript模块
     * @param pipeline 基础流水线实例
     * @param config 语言特定配置
     */
    TypeScriptModule(BasePipeline pipeline, config = [:]) {
        this.pipeline = pipeline
        if (config.packageManager) this.packageManager = config.packageManager
        if (config.testTool) this.testTool = config.testTool
        if (config.testReportPath) this.testReportPath = config.testReportPath
        if (config.coverageTool) this.coverageTool = config.coverageTool
        if (config.buildCommand) this.buildCommand = config.buildCommand
        pipeline.script.echo "初始化TypeScript模块，包管理器: ${packageManager}"
    }

    /**
     * 安装npm/yarn依赖
     */
    void installDependencies() {
        pipeline.script.stage("前端依赖安装") {
            pipeline.script.echo "使用${packageManager}安装依赖..."
            def installCommand = packageManager == 'yarn' ? 'yarn install --frozen-lockfile' : 'npm ci'
            pipeline.script.sh(installCommand)
            pipeline.script.echo "前端依赖安装完成"
        }
    }

    /**
     * 构建TypeScript项目
     */
    void build() {
        pipeline.script.stage("TypeScript构建") {
            pipeline.script.echo "构建TypeScript项目..."
            def buildCmd = packageManager == 'yarn' ? "yarn run ${buildCommand}" : "npm run ${buildCommand}"
            pipeline.script.sh(buildCmd)
            pipeline.script.echo "TypeScript项目构建完成"
        }
    }

    /**
     * 运行单元测试
     * @param args 测试参数
     */
    void runUnitTests(String args = '') {
        pipeline.script.stage("前端单元测试") {
            pipeline.script.echo "运行${testTool}单元测试..."
            pipeline.script.sh "mkdir -p ${testReportPath}"
            def testCmd = packageManager == 'yarn' ? 
                "yarn run test ${args} --ci --reporters=default --reporters=jest-junit" : 
                "npm run test ${args} -- --ci --reporters=default --reporters=jest-junit"
            pipeline.script.sh(testCmd)

            // 发布测试报告
            pipeline.script.junit "${testReportPath}/junit.xml"
            pipeline.script.echo "前端单元测试完成，报告已生成"
        }
    }

    /**
     * 运行E2E测试
     * @param testDir E2E测试目录
     * @param args 测试参数
     */
    void runE2ETests(String testDir = 'e2e', String args = '') {
        pipeline.script.stage("前端E2E测试") {
            pipeline.script.echo "运行E2E测试..."
            def e2eCmd = packageManager == 'yarn' ? 
                "yarn run e2e ${testDir} ${args}" : 
                "npm run e2e ${testDir} ${args}"
            pipeline.script.sh(e2eCmd)
            pipeline.script.echo "前端E2E测试完成"
        }
    }

    /**
     * 生成代码覆盖率报告
     */
    void generateCoverageReport() {
        pipeline.script.stage("前端代码覆盖率") {
            pipeline.script.echo "生成代码覆盖率报告..."
            def coverageCmd = packageManager == 'yarn' ? 
                "yarn run ${coverageTool} ${testTool}" : 
                "npm run ${coverageTool} ${testTool}"
            pipeline.script.sh(coverageCmd)

            // 发布覆盖率报告
            pipeline.script.publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: 'coverage',
                reportFiles: 'index.html',
                reportName: 'TypeScript Code Coverage Report'
            ])
            pipeline.script.echo "前端代码覆盖率报告生成完成"
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
            def sonarCommand = "sonar-scanner -Dsonar.projectKey=${projectKey} -Dsonar.sources=. -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info ${args}"
            pipeline.script.sh(sonarCommand)

            // 记录质量检查结果
            pipeline.recordQualityResult("SonarQube", true, [status: "passed", projectKey: projectKey])
            pipeline.script.echo "SonarQube扫描完成"
        }
    }
}