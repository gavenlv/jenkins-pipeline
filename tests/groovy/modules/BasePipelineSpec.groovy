package modules

import spock.lang.Specification
import spock.lang.Mock
import spock.lang.Unroll

class BasePipelineSpec extends Specification {

    @Mock
    def script

    def "测试流水线初始化"() {
        given:
        def config = [projectName: 'test-project', qualityGateEnabled: true]

        when:
        def pipeline = new BasePipeline(script, config)

        then:
        pipeline.script == script
        pipeline.config == config
        pipeline.stages.isEmpty()
        pipeline.parallelStages.isEmpty()
        pipeline.qualityGateResults.isEmpty()
    }

    def "添加和执行串行阶段"() {
        given:
        def pipeline = new BasePipeline(script, [:])
        def stageAction = Mock(Closure)

        when:
        pipeline.addStage('测试阶段', stageAction)
        pipeline.execute()

        then:
        pipeline.stages.size() == 1
        pipeline.stages[0].name == '测试阶段'
        1 * stageAction.call()
    }

    def "添加和执行并行阶段"() {
        given:
        def pipeline = new BasePipeline(script, [:])
        def parallelAction1 = Mock(Closure)
        def parallelAction2 = Mock(Closure)

        when:
        pipeline.addParallelStage('并行阶段1', parallelAction1)
        pipeline.addParallelStage('并行阶段2', parallelAction2)
        pipeline.execute()

        then:
        pipeline.parallelStages.size() == 2
        '并行阶段1' in pipeline.parallelStages.keySet()
        '并行阶段2' in pipeline.parallelStages.keySet()
        1 * parallelAction1.call()
        1 * parallelAction2.call()
    }

    def "质量门禁检查通过"() {
        given:
        def pipeline = new BasePipeline(script, [qualityGateEnabled: true])
        pipeline.recordQualityResult('测试检查', true, [status: 'passed'])
        pipeline.recordQualityResult('安全检查', true, [status: 'passed'])

        when:
        pipeline.executeQualityGate()

        then:
        noExceptionThrown()
    }

    def "质量门禁检查失败"() {
        given:
        def pipeline = new BasePipeline(script, [qualityGateEnabled: true])
        pipeline.recordQualityResult('测试检查', true, [status: 'passed'])
        pipeline.recordQualityResult('安全检查', false, [status: 'failed'])

        when:
        pipeline.executeQualityGate()

        then:
        thrown(Exception)
    }
}