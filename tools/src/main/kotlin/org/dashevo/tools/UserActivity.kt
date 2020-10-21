package org.dashevo.tools

import java.io.File

class UserActivity {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val proc1 = ProcessBuilder("./network-activity ${args[0]}").start()
            proc1.waitFor()

            File("list.txt").forEachLine {
                val proc1 = ProcessBuilder("./wallet-tool create --seed=\"$it\" --net=MOBILE --force").start()
                proc1.waitFor()
            }
        }
    }
}