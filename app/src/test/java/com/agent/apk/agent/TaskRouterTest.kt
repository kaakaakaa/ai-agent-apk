// File: app/src/test/java/com/agent/apk/agent/TaskRouterTest.kt
package com.agent.apk.agent

import com.agent.apk.infra.DeviceCapabilityProvider
import com.agent.apk.infra.DeviceTier
import com.agent.apk.model.AppSettings
import com.agent.apk.model.Task
import org.junit.Test
import org.junit.Assert.*

/**
 * TaskRouter 单元测试
 *
 * 测试范围：
 * - 网络状态对路由的影响
 * - 设备能力对路由的影响
 * - 用户设置对路由的影响
 * - 简单/复杂任务的识别
 */
class TaskRouterTest {

    @Test
    fun `routes to LOCAL when offline`() {
        val networkStatus = FakeNetworkStatus(isOffline = true)
        val router = TaskRouter(
            networkStatus = networkStatus,
            deviceCapabilityProvider = FakeDeviceCapabilityProvider(DeviceTier.HIGH),
            settings = FakeAppSettings(cloudFirst = false)
        )

        val task = Task(id = "1", userInstruction = "打开微信")
        val target = router.route(task)

        assertEquals(AgentTarget.LOCAL, target)
    }

    @Test
    fun `routes to CLOUD when cloudFirst is true`() {
        val networkStatus = FakeNetworkStatus(isOffline = false)
        val router = TaskRouter(
            networkStatus = networkStatus,
            deviceCapabilityProvider = FakeDeviceCapabilityProvider(DeviceTier.HIGH),
            settings = FakeAppSettings(cloudFirst = true)
        )

        val task = Task(id = "1", userInstruction = "打开微信")
        val target = router.route(task)

        assertEquals(AgentTarget.CLOUD, target)
    }

    @Test
    fun `routes simple command to LOCAL on HIGH tier device`() {
        val networkStatus = FakeNetworkStatus(isOffline = false)
        val router = TaskRouter(
            networkStatus = networkStatus,
            deviceCapabilityProvider = FakeDeviceCapabilityProvider(DeviceTier.HIGH),
            settings = FakeAppSettings(cloudFirst = false)
        )

        val task = Task(id = "1", userInstruction = "打开微信")
        val target = router.route(task)

        assertEquals(AgentTarget.LOCAL, target)
    }

    @Test
    fun `routes simple command to LOCAL on MEDIUM tier device`() {
        val networkStatus = FakeNetworkStatus(isOffline = false)
        val router = TaskRouter(
            networkStatus = networkStatus,
            deviceCapabilityProvider = FakeDeviceCapabilityProvider(DeviceTier.MEDIUM),
            settings = FakeAppSettings(cloudFirst = false)
        )

        val task = Task(id = "1", userInstruction = "返回")
        val target = router.route(task)

        assertEquals(AgentTarget.LOCAL, target)
    }

    @Test
    fun `routes simple command to CLOUD on LOW tier device`() {
        val networkStatus = FakeNetworkStatus(isOffline = false)
        val router = TaskRouter(
            networkStatus = networkStatus,
            deviceCapabilityProvider = FakeDeviceCapabilityProvider(DeviceTier.LOW),
            settings = FakeAppSettings(cloudFirst = false)
        )

        val task = Task(id = "1", userInstruction = "打开微信")
        val target = router.route(task)

        assertEquals(AgentTarget.CLOUD, target)
    }

    @Test
    fun `routes complex task to CLOUD regardless of device tier`() {
        // 高端设备
        val networkStatus1 = FakeNetworkStatus(isOffline = false)
        val router1 = TaskRouter(
            networkStatus = networkStatus1,
            deviceCapabilityProvider = FakeDeviceCapabilityProvider(DeviceTier.HIGH),
            settings = FakeAppSettings(cloudFirst = false)
        )
        val task1 = Task(id = "1", userInstruction = "帮我订一杯咖啡")
        assertEquals(AgentTarget.CLOUD, router1.route(task1))

        // 中端设备
        val networkStatus2 = FakeNetworkStatus(isOffline = false)
        val router2 = TaskRouter(
            networkStatus = networkStatus2,
            deviceCapabilityProvider = FakeDeviceCapabilityProvider(DeviceTier.MEDIUM),
            settings = FakeAppSettings(cloudFirst = false)
        )
        val task2 = Task(id = "2", userInstruction = "把这张照片发给张三")
        assertEquals(AgentTarget.CLOUD, router2.route(task2))

        // 低端设备
        val networkStatus3 = FakeNetworkStatus(isOffline = false)
        val router3 = TaskRouter(
            networkStatus = networkStatus3,
            deviceCapabilityProvider = FakeDeviceCapabilityProvider(DeviceTier.LOW),
            settings = FakeAppSettings(cloudFirst = false)
        )
        val task3 = Task(id = "3", userInstruction = "找出未接来电并回复")
        assertEquals(AgentTarget.CLOUD, router3.route(task3))
    }

    @Test
    fun `recognizes open app commands as simple`() {
        val task1 = Task(id = "1", userInstruction = "打开微信")
        assertTrue(task1.isSimpleCommand())

        val task2 = Task(id = "2", userInstruction = "启动支付宝")
        assertTrue(task2.isSimpleCommand())

        val task3 = Task(id = "3", userInstruction = "进入设置")
        assertTrue(task3.isSimpleCommand())
    }

    @Test
    fun `recognizes navigation commands as simple`() {
        assertTrue(Task(id = "1", userInstruction = "返回").isSimpleCommand())
        assertTrue(Task(id = "2", userInstruction = "主页").isSimpleCommand())
        assertTrue(Task(id = "3", userInstruction = "首页").isSimpleCommand())
        assertTrue(Task(id = "4", userInstruction = "最近任务").isSimpleCommand())
    }

    @Test
    fun `recognizes click commands as simple`() {
        assertTrue(Task(id = "1", userInstruction = "点击确定按钮").isSimpleCommand())
        assertTrue(Task(id = "2", userInstruction = "点一下").isSimpleCommand())
    }

    @Test
    fun `recognizes system commands as simple`() {
        assertTrue(Task(id = "1", userInstruction = "调高音量").isSimpleCommand())
        assertTrue(Task(id = "2", userInstruction = "打开蓝牙").isSimpleCommand())
        assertTrue(Task(id = "3", userInstruction = "关闭 WiFi").isSimpleCommand())
    }

    @Test
    fun `recognizes complex tasks as not simple`() {
        assertFalse(Task(id = "1", userInstruction = "帮我订一杯咖啡").isSimpleCommand())
        assertFalse(Task(id = "2", userInstruction = "把这张照片发给张三").isSimpleCommand())
        assertFalse(Task(id = "3", userInstruction = "找出未接来电并回复").isSimpleCommand())
    }
}

// Fake 实现
class FakeNetworkStatus(private val isOffline: Boolean) : NetworkStatus {
    override fun isOffline(): Boolean = isOffline
}

class FakeDeviceCapabilityProvider(private val tier: DeviceTier) : DeviceCapabilityProvider {
    override fun getDeviceTier(): DeviceTier = tier
}

class FakeAppSettings(val cloudFirst: Boolean) : AppSettings() {
    override val cloudFirstValue: Boolean get() = cloudFirst
}
