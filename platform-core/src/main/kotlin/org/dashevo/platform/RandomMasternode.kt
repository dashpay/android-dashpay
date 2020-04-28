package org.dashevo.platform

import org.dashevo.dapiclient.MasternodeService
import kotlin.random.Random

class RandomMasternode(val masternodeList: Array<String>) : MasternodeService {
    override fun getServer(): String {
        return masternodeList[Random.nextInt(masternodeList.size)]
    }
}