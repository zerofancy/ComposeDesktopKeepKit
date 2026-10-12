package smoke

import oshi.driver.windows.perfmon.ProcessorInformation.InterruptsProperty
import oshi.driver.linux.proc.ProcessStat.PidStat
import oshi.driver.linux.proc.DiskStats.IoStat
import oshi.SystemInfo
import oshi.driver.windows.wmi.Win32OperatingSystem.OSVersionProperty
import oshi.util.platform.windows.WmiUtil
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery

fun main() {
    // Exercise Windows enum reflection without invoking COM on the host OS.
    val query = WmiQuery("Win32_OperatingSystem", OSVersionProperty::class.java)
    check(WmiUtil.queryToString(query) == "SELECT VERSION,PRODUCTTYPE,BUILDNUMBER,CSDVERSION,SUITEMASK FROM Win32_OperatingSystem")
    // These enum contracts are shared by generic PDH and Linux proc helpers.
    listOf(InterruptsProperty::class.java, PidStat::class.java, IoStat::class.java).forEach {
        check(!it.enumConstants.isNullOrEmpty()) { "Missing reflective enum values: $it" }
    }
    val system = SystemInfo()
    val hardware = system.hardware
    check(hardware.memory.total > 0)
    check(hardware.processor.logicalProcessorCount > 0)
    val interfaces = hardware.getNetworkIFs(true)
    check(interfaces.isNotEmpty())
    interfaces.forEach {
        check(it.name.isNotBlank())
        check(it.updateAttributes()) { "Cannot update network interface ${it.name}" }
        check(it.bytesRecv >= 0 && it.bytesSent >= 0)
        check(it.queryNetworkInterface() != null)
    }
    check(system.operatingSystem.processId > 0)
    println("PASS oshi native hardware and network counters: ${interfaces.size} interfaces")
}

// Deliberately unreachable: verification checks that Release shrinking removes it.
private class UnusedSmokeMarker
