package org.dashevo.platform

import org.bitcoinj.core.Base58
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.params.EvoNetParams
import org.dashevo.dapiclient.model.DocumentQuery
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.toHexString
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class NamesTest {
    @Test
    fun saltTest() {
        val json = "{preorderSalt: ybMDp5vFUcVfucP9y8NNQecdVfUoA1XA9r, \$userId: Gm7PjQ2ekS4Q5YLSP8Fo1fLDJUSvwY6s8NZjEzF5qdeK, \$entropy: yNKtkioH2GpECL2ZxCDQYVSzXJDxsX4dDZ, normalizedParentDomainName: dash, normalizedLabel: test-adeline73, records: {dashIdentity: Gm7PjQ2ekS4Q5YLSP8Fo1fLDJUSvwY6s8NZjEzF5qdeK}, nameHash: 56208aa0e20263e5dc787c3cb8a3f99cbc60701af2b2a5fa39e13d627e53bbb66b8a, label: test-Adeline73, \$rev: 1, \$type: domain, \$contractId: 77w8Xqn25HwJhjodrHW133aXhjuTsTv9ozQaYpSHACE3}"

        val document = Document(JSONObject(json).toMap())

        //validate salt

        val fullDomainName = document.data["normalizedLabel"] as String + "." + document.data["normalizedParentDomainName"]
        val nameHash = Sha256Hash.twiceOf(fullDomainName.toByteArray())

        assertEquals(document.data["nameHash"] as String, "5620$nameHash")

        val preOrderSaltRaw = Base58.decode(document.data["preorderSalt"] as String)

        val baos = ByteArrayOutputStream(preOrderSaltRaw.size + nameHash.bytes.size)
        baos.write(preOrderSaltRaw)
        baos.write(0x56)
        baos.write(0x20)
        baos.write(nameHash.bytes)

        val saltedDomainHash = Sha256Hash.twiceOf(baos.toByteArray()).toString()

        var platform = Platform(EvoNetParams.get())
        val contractId = "77w8Xqn25HwJhjodrHW133aXhjuTsTv9ozQaYpSHACE3"
        try {
            //devnet-evonet
            val where2 = listOf(
                listOf("saltedDomainHash", "startsWith", "5620$saltedDomainHash").toMutableList()
            ).toMutableList()

            val empty = listOf<String>().toMutableList()
            val documents = platform.client.getDocuments(contractId, "preorder", DocumentQuery.Builder().where(where2).build())
            val list = documents!!.map { platform.dpp.document.createFromSerialized(it) }
            println(list.size)
        } catch (e: Exception) {

        }
    }
}