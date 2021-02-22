package org.dashevo.platform;
import org.bitcoinj.params.MobileDevNetParams
import org.bitcoinj.params.TestNet3Params
import org.dashevo.dapiclient.DapiClient
import org.junit.jupiter.api.AfterEach

open class PlatformNetwork {

    val platform = Platform(TestNet3Params.get())

    @AfterEach
    fun afterEachTest() {
        println(platform.client.reportNetworkStatus())
    }

    private fun getMnList(): List<Map<String, Any>> {
        val success = 0
        do {
            try {
                val baseBlockHash = platform.client.getBlockHash(0)
                val blockHash = platform.client.getBestBlockHash()

                val mnListDiff = platform.client.getMnListDiff(baseBlockHash!!, blockHash!!)
                return mnListDiff!!["mnList"] as List<Map<String, Any>>
            } catch (e: Exception) {
                println("Error: $e")
            }
        } while (success == 0)
        return listOf()
    }

    init {
        println("initializing platform")
        platform.client = DapiClient("174.34.233.123", false)
        val mnList = getMnList()
        val validList = mnList.filter {
            it["isValid"] == true
        }
        println("${validList.size} masternodes were found")
        platform.client = DapiClient(validList.map { (it["service"] as String).split(":")[0] })
    }
}
