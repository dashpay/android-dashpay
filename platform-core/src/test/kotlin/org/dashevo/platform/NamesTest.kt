package org.dashevo.platform

import org.dashevo.dpp.identifier.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NamesTest : PlatformNetwork() {

    @Test
    fun getTest() {
        val label = "x-hash-eng"
        val ownerId = "CtbiVfTcKFpt5dhmWYHwK9yp1CKR5dci9WdHokYGqyjG";

        val byLabel = DomainDocument(platform.names.get(label)!!)
        val byResolve = DomainDocument(platform.names.resolve("$label.${Names.DEFAULT_PARENT_DOMAIN}")!!)
        val byLabelAndDomain = DomainDocument(platform.names.get(label, Names.DEFAULT_PARENT_DOMAIN)!!)
        val byRecord = DomainDocument(platform.names.resolveByRecord("dashUniqueIdentityId", ownerId).first())
        val bySearch = DomainDocument(platform.names.search(label, Names.DEFAULT_PARENT_DOMAIN, retrieveAll = false, limit = 1).first())

        assertEquals(label, byLabel.label)
        assertEquals(label, byResolve.label)
        assertEquals(label, byLabelAndDomain.label)
        assertEquals(label, byRecord.label)
        assertEquals(label, bySearch.label)
    }

    @Test
    fun getListTest() {
        val bySearch = platform.names.search("x-hash", Names.DEFAULT_PARENT_DOMAIN, retrieveAll = false, limit = 10).sortedBy { it.id.toString() }

        val ids = bySearch.map {
            DomainDocument(it).dashUniqueIdentityId!!
        }

        val byGetList = platform.names.getList(ids).sortedBy { it.id.toString() }

        assertEquals(bySearch.size, byGetList.size)
        for (i in bySearch.indices) {
            assertTrue(bySearch[i] == byGetList[i])
        }
    }

    @Test
    fun domainTest() {
        val fullName = "username.dash"

        val (domain, label) = platform.names.normalizedNames(fullName)
        assertEquals("username", label)
        assertEquals(Names.DEFAULT_PARENT_DOMAIN, domain)
    }
    
    @Test
    fun getLargeListTest() {
        val userIds = arrayListOf(
            "22eVBEwtoYUyoZi9Mtgf2JDdTB1wNh3ZuJk9ErQdun9V",
            "9jzNgQYbmNNe8JZXzue8uVm8nhuNyYVvk5WuNw4PAQx3",
            "EfwwJBN58xh1r355k9r1h7jsEGpFBYPPTH18f7vqGCsz",
            "62LZBraqUQCHJF5JgyVSLkPd8R3u1ZjRJLLtewgfi4T1",
            "FjiwBJU3jximPdGMTGnszQf3w4CsPemtrqErNgFT19WL",
            "75B3UGMEvo8pwE9Ch3tgDgoYwaJqFVqYfooqm1qiUoSM",
            "AAqmpHBTpjyuXEjT8UVp8GfV5BaD26v5UGHZfyQ69bmG",
            "6nE3hbvPAcD4tHBugw2LkvAtLYhDQBtHgpvMUWYXwk7Q",
            "3pSytwMP6N5ZpyiPiD78YzsGvzqL52HzzE9thKvC4AWc",
            "3zWeuqGQCfvaC68jSGyTQGxDYGw9FoX2JmyYZL1DDyex",
            "5y69D5k71omozvFU55gv3os62Ynwd8DstyJFUCdiZr1P",
            "2QDvH2wjSFnFEK29mGhaEmm7Ti2uERHk6zHUFHSe7wpd",
            "2jpYgQSEpk5VcTgzPPpCDCR7VzZ8oV8sGK1jP6CwNFmh",
            "CbtmMayo3WwZFC4sfW3us4sjjsxmPFEoU5yW9QuRRPSa",
            "HvKGW7C8d62kTme8YiDAspQ5rYRYvrQStKmsLoX4JK9v",
            "E2CtXTuJTrFfcQssDvawQFRP3FmLsWPCvCqsdsRk5s6W",
            "42U2Asr4MM1rkWXtPmdFQPfpo1rRximyG3Zbp1fXBBpp",
            "DUhB2ytaHcZdNj6sMm4pQ8ncjfeKHLYpCkGPzA5suhqG",
            "HK1jnhxWMDuoEwkvKXTXFR5WeTYzx4Vm9C7Yx9qPhWXC",
            "7iJKRr32gKpwthRhiw8UiEYPJdUACbRsUe145YNbkLgL",
            "HKsZWTiQXKGMZXEmnq6yPU85oGBvTGFWoehjsWoZjvUC",
            "GKLApSLrTR9qJMD7riLiF6R62mAgcH8PASobe9KrH5Rt",
            "7mFVxcUdM8HcQaNm3ooMuZmbXVzn9c4B9SS39mEvUF3S",
            "EhLCwT3cJx7iZJjmmeQFgkrQSL2osZngrZcvBxjZmJVn",
            "GkiBLaGYGq8kJCTUeHepVdGkweVZnRAgNCiwpkFkFRi8",
            "2Jc1QfGrPoZbw4QTNmAHAVQ66cXPt7iiZwcYtrCSKLC7",
            "DRstBnVF5jLL3FNeo5bVX28yTaNMGrvCgiZJjkQD86BW",
            "CEGrAfWwRb7jEQfCw7Qv5YyvdGZ6UFS9dqJdurUicinv",
            "3Gb9iFxEQf3GnzzwssCCstvbrhxEoXGgFMKpnSE7Rm1u",
            "77L2v6K6k6UBjkK6eDn5UrP1Qb8iZiE7XBC3U2R3oZjG",
            "nb7SYoh1dSmGTPXW29Qv6yS4maLWi8zuAzxc3XyWQz4",
            "E9G4zYBpNaL72AW5eWRxn7jRAY1ndMYMeaHS1oHMFVhR",
            "EmT8DEbVU9faT1h1aiD4FFmEkjGnB4xb1zic9V6zDLEU",
            "BSft8H3ySmbVLofBbGE6gF32JJPXKQE9SatMgpvdeyU5",
            "FDUN2LKQTGDpx431vz7mgXabYez8XjDRsy7PMEYafATi",
            "B6D6Ku1Rr2UKbdzsUXjoTHe2BfxUcwSXs4Lsf7DTuBkg",
            "AMjoZM9tnVVbrAwBn7SZPTY1uZowUM5LDKf6sYiWxKAa",
            "2znV9jpDwKu47nJWSor2Un7GbmqWyApWwoAAHDUy2tWd",
            "CzNfNy8HVEnbfwyTVLJi2bLtgH3fjmPsgXtoMjAY3CFp",
            "8SBy4TjJvZYG9npUYGmh5mePfbzCanhHk5BSf5tApigc",
            "B152W9FqtLe4UkkHXx5Q95XfgpgUcYoS4MXdeEoQ96fK",
            "AivvUgA6xXoR13pQruTfwLfaDcLq3oyvyHgfBm5X1zzX",
            "21eu6VjtMnnaLTABGNoajzwrp3kQzKcZJZgXX2A78RFV",
            "JC4wCyx8Sa2yphxF9ydCuhX4Nvh4jhYuthGYwRQtcDWa",
            "AzwNJZT187hZfzsnLsGptsxXQEP3wzRtxTVznS2FVzSA",
            "6uteVpbBnSzs9pKt6z5wDNju8k5neoqw9BVq2M4Nsjhk",
            "84dLTLk3LALjrkwxtdsVQrZsAsEEymXayYBc465xoH24",
            "DWJkJ87RiSgZSrmfGqsPnCqszx4xWzgR438eUgDj65EF",
            "C4bXDupUyUyRVDTn3VkEHRdQhz44iKYg6ReSbzuN58TU",
            "f5biqiFVoQNLgGCSNBRYmFrVp4JJJWNBm4uUsqFshDE",
            "9K16Q2SjYazmGBVvtdcxnyFeWainkknFQwhRuiSVvg7E",
            "G7LP63Mx36YkDjt1AHPtS9THbMqbhZ9t879XGAmqtMyK",
            "DcwHHHaAjuM63Ue6T7WfjsdjPnyhDSwe3KyWV45fnZ6n",
            "9cASRpQwoqhPovk7tWQnGGkhvFS4A1V4XVzSSLJnim9K",
            "cHTLshigz8dbUbVR1mrNq2n3fgcMdvs6skUNB3s9dEU",
            "Cb1n8wVv2QpGgPcoLkRefZeRyKaHWfger3dgBhWCdDZp",
            "BSi9gcccVSoowE8NVuUwiK2LTThmm6nt6qxwnhXygthd",
            "H1wB7XYMEtn3XKXtE46TGoTsk127EAsEtxti6diNoqRo",
            "H1QTzeb9Lmufhn5gmPz4K8kHqfp4MT8Y5eV9iADxL8im",
            "DS8H29vB6jjXcN5cVdSxWyxY2apX5zTfxg2MdTTuruqe",
            "BjWMGhSnTeJnTuPTXLg8zL8fLAg23JxTE2fnKAr3oXKp",
            "HVDmfAZGNDzFaF3ZDxaXR16omHBggw5Qe6EwSVmscJKo",
            "Mj8d4zqxDbMTtCjwApgw1MjgkwphRWcqqwY6W6JyFFM",
            "3PkL7NE44VwsUpAPd3MJvc61wFqTq2ESNqkEiHNu8YFU",
            "FHfseAibcEZzarmGo54dLnJr3NbP2hMoTr3peMR3A4V5",
            "6KZn8Ydc4MBUkfb9CRc7WUaT3DYfm3GqoJuhUa92dyqa",
            "H6sHKPDE9cV2AnYdbLSdnKsgLfU1nteWmZkKBzTem2Fv",
            "2dmkZxo8dWFKTFypCoBdQxeRQo39UWTZrhwxQHbcbLzR",
            "9VnNVQVsSQToahFaEXRYUz5oX4Erm2rvmBCsJKQpeKtq",
            "5RsbPjGy1WzpW9KpgGnFhfh9EbKryuxFh5xVkuFnvUHo",
            "5gLTsLcLptVfjqscKUQFxp5aGARKNFvhz1LqLRQaSDZd",
            "4fd6ZCEtaBqTpgm8yhyQFwK1YPzPu7nasCdSaBhQS1Zs",
            "FjyMNtVVCCJiNaVucVoWD5fJKjJsduKNbifQ13kT89FY",
            "3zH7erBzf74VXpzSKcM6zQWornSyux2Tey2FZMKcLf1e",
            "E5qH3viShhMUqtRPj52moADS74thX4LX7eYMJmeMQerk",
            "4ozujBbe2J5weAZVMTAbT8S7ZWswguhTrbuQXqeS97J3",
            "Et2SyXqG33AyCmifYYb2q5dfj3MKL3itxzJKntWq2YTW",
            "6CpAyW9UuEJUy9tW7gY6VKrvSvihquhstUDZ3dWtHcTu",
            "H148A83sXuR2K8obDkRZKFptJmNTahd2xA2nSEbET9Bk",
            "8zYR4cKn1ceBhzpiexyT6JaoGeZq7TRQP5Pb28cW6b1f",
            "JE1nyH4NepwqrecYbxgYof1a5ZdTmsXrLmR6tpqo6sbL",
            "Ct7LJgtvc66YgGwu141R6UPDbzgYR5SJYR7psDVrW7r8",
            "J8GMdg41u1Z8tQdRqCSxssfR1Agew9UEAnExkK65qyk7",
            "2v4m8Lgk1RK79HXAMi2c5SA84QGjZmv3VVzydMi4hDxr",
            "Dz4GTaTet6ugyqTf1cwZSNGToACHAfQVJgZtbxAXfSVM",
            "9QwiUZLyUPuyJVTgD56mPnYtUEzM7b2FN9mZSAYXbhCZ",
            "8GyUya1kxg3VhfK4i48GiCmFhWQR8XZKWzWBhxzZPgbb",
            "E6JSe7LokxZtQ1Adth2ZtMqWZHXzVZ3DwsqPN7sZeZ41",
            "9LuaFv6JpW9JAnwyVeGncvjhZyZU5nwn1janjnhBZxaZ",
            "3NoQnpcs9btpMCJkXJrPpid5jNxabv8uZPth8JUMjHcg",
            "2CrLDpvd68RvbtoVE8DFLiESCZJWggWGShtEanYDZG5A",
            "5G7KhW649MfWZXpiHA46ZG24BbDh5Yi8bJ5zzY6oGBYK",
            "De46EwViwmdrQrMxY7yjvk5GanbHniATBM4FBpcaVbxi",
            "9PaR8mcYVwtuUF7rNa3vtTCrvuzs2d2m6gPA2NvRtte1",
            "CqjRHA8fnyyrPZxb6pnMKsmQfHbFXPa3Vmgej8eZELMw",
            "5yTGyaVaj5kC5sH89j7D5BWZbwJJ8rWh7cT5EBzQjF4J",
            "92LhzDzBj8AcPgo3dx53EqVtHuNMdFvLKrYZAb9n3Rjz",
            "AXECPDK4cE9ciYgutxJ8UAXEXGEDaUC3SjYD1tvut3vC",
            "6MeUBzSUNPt676dSAoQZDPRUqaouTG1NgFNnN8ZnPfk4",
            "2DFY9zgboU8Z7XwMQXzUV5LmsBcjz82WdWtjL2fVRzva",
            "FnKx6VsbSKXgekU9Sb5twoCvy9pQCQXihxumgctHNLvg",
            "HFQ7badSVw2tTiRaYaDrtZthCZQT1NBF33FH5qjcK8MZ",
            "A258SAfejK1yAFLyJD4SeK3mUnuaEw4h5eTnjiKziKGJ",
            "HM9iGTQxMrVab3nLkVBneWLnA6hfuxXE8i5BV2t64a3C",
            "3M5KzNS9Azcupw7i3WXt9RDcf64hU4HWJrrHrr7LEuEd",
            "Ez8Pp7ogFsBJGoyF1x5dCidQMd32C7WKSLLLvivZG2Po",
            "8h4LEvwymMy7hKeUEKyYTomnewjx5SnwUeDHPMGULo4r")

        val list = platform.names.getList(userIds.map { Identifier.from(it) })
        val nameList = list.map { DomainDocument(it) }

        for (id in userIds) {
            assertTrue(nameList.find { it.dashUniqueIdentityId.toString() == id } != null)
        }
        assertTrue(list.size == userIds.size)
    }
}