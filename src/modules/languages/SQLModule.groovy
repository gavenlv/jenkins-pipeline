package modules.languages

import modules.BasePipeline

/**
 * SQL语言专用CI模块，提供数据库脚本验证、测试和部署功能
 */
class SQLModule {
    private BasePipeline pipeline
    private String sqlLinter = 'sqlfluff'
    private String testFramework = 'tSQLt'
    private String migrationTool = 'flyway'
    private String reportPath = 'sql-reports'
    private String jdbcUrl
    private String dbUser
    private String dbPassword

    /**
     * 初始化SQL模块
     * @param pipeline 基础流水线实例
     * @param config 数据库配置
     */
    SQLModule(BasePipeline pipeline, config = [:]) {
        this.pipeline = pipeline
        this.sqlLinter = config.sqlLinter ?: sqlLinter
        this.testFramework = config.testFramework ?: testFramework
        this.migrationTool = config.migrationTool ?: migrationTool
        this.reportPath = config.reportPath ?: reportPath
        this.jdbcUrl = config.jdbcUrl ?: pipeline.script.error("必须指定JDBC连接URL")
        this.dbUser = config.dbUser ?: pipeline.script.error("必须指定数据库用户名")
        this.dbPassword = config.dbPassword ?: pipeline.script.error("必须指定数据库密码")
        pipeline.script.echo "初始化SQL模块，迁移工具: ${migrationTool}, 测试框架: ${testFramework}"
    }

    /**
     * 执行SQL代码风格检查
     * @param sqlDir SQL脚本目录
     */
    void lintSql(String sqlDir = 'sql') {
        pipeline.script.stage("SQL代码风格检查") {
            pipeline.script.echo "使用${sqlLinter}执行SQL代码风格检查..."
            pipeline.script.sh "mkdir -p ${reportPath}"

            // 安装SQL linter(如果未安装)
            def installCmd = pipeline.script.isUnix() ? 
                "pip install ${sqlLinter}" : 
                "pip install ${sqlLinter}"
            pipeline.script.sh "which ${sqlLinter} || ${installCmd}"

            // 执行SQL linting
            def lintCmd = "${sqlLinter} lint ${sqlDir} --format json --output ${reportPath}/sql-lint-report.json"
            pipeline.script.sh(lintCmd)

            // 发布报告
            pipeline.script.publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: reportPath,
                reportFiles: 'sql-lint-report.html',
                reportName: 'SQL Linting Report'
            ])

            // 记录质量检查结果
            pipeline.recordQualityResult("SQL Linting", true, [status: "passed", reportPath: "${reportPath}/sql-lint-report.json"])
            pipeline.script.echo "SQL代码风格检查完成"
        }
    }

    /**
     * 执行数据库迁移测试
     * @param migrationDir 迁移脚本目录
     */
    void testMigrations(String migrationDir = 'sql/migrations') {
        pipeline.script.stage("数据库迁移测试") {
            pipeline.script.echo "使用${migrationTool}测试数据库迁移..."
            pipeline.script.sh "mkdir -p ${reportPath}"

            // 执行迁移测试(使用内存数据库)
            def migrationCmd = "${migrationTool} migrate -url=jdbc:h2:mem:testdb -user=sa -password= -locations=files:${migrationDir} -validateOnMigrate=true"
            pipeline.script.sh(migrationCmd)

            // 记录质量检查结果
            pipeline.recordQualityResult("Database Migration Test", true, [status: "passed", migrations: migrationDir])
            pipeline.script.echo "数据库迁移测试完成"
        }
    }

    /**
     * 运行SQL单元测试
     * @param testDir 测试脚本目录
     */
    void runUnitTests(String testDir = 'sql/tests') {
        pipeline.script.stage("SQL单元测试") {
            pipeline.script.echo "使用${testFramework}运行SQL单元测试..."
            pipeline.script.sh "mkdir -p ${reportPath}"

            // 根据测试框架执行不同命令
            def testCmd
            if (testFramework == 'tSQLt') {
                testCmd = "sqlcmd -S localhost -d testdb -U ${dbUser} -P ${dbPassword} -i ${testDir}/run_tests.sql -o ${reportPath}/sql-test-results.txt"
            } else if (testFramework == 'pgTAP') {
                testCmd = "psql -d ${jdbcUrl} -U ${dbUser} -f ${testDir}/run_tests.sql -o ${reportPath}/sql-test-results.txt"
            } else {
                pipeline.script.error("不支持的SQL测试框架: ${testFramework}")
            }

            pipeline.script.sh(testCmd)

            // 发布测试报告
            pipeline.script.publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: reportPath,
                reportFiles: 'sql-test-results.txt',
                reportName: 'SQL Test Results'
            ])

            // 记录质量检查结果
            pipeline.recordQualityResult("SQL Unit Tests", true, [status: "passed", testDir: testDir])
            pipeline.script.echo "SQL单元测试完成"
        }
    }

    /**
     * 执行数据库部署
     * @param migrationDir 迁移脚本目录
     * @param environment 目标环境
     */
    void deploy(String migrationDir = 'sql/migrations', String environment = 'dev') {
        pipeline.script.stage("SQL部署到${environment}") {
            pipeline.script.echo "使用${migrationTool}部署SQL到${environment}环境..."

            // 执行实际数据库迁移
            def migrationCmd = "${migrationTool} migrate -url=${jdbcUrl} -user=${dbUser} -password=${dbPassword} -locations=files:${migrationDir}"
            pipeline.script.sh(migrationCmd)

            pipeline.script.echo "SQL成功部署到${environment}环境"
        }
    }
}