package com.Exchange

import net.corda.core.identity.Party
import net.corda.core.toFuture
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.issuedBy
import net.corda.finance.workflows.getCashBalances
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetNotaryConfig
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class RPC_Exchange {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeA : StartedMockNode
    private lateinit var nodeB : StartedMockNode
    private lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(threadPerNode = true, cordappPackages = listOf("net.corda.finance"))
        nodeA = mockNet.createPartyNode()
        nodeB = mockNet.createPartyNode()
        println("nodeB registerInititatedflow")
        nodeB.registerInitiatedFlow(ForeignExchangeRemoteFlow::class.java)
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUP(){
        mockNet.stopNodes()
    }


    @Test
    fun Exchange (){

        nodeA.startFlow(CashIssueFlow(DOLLARS(1000),
                OpaqueBytes.of(0x01),
                notary
        )).getOrThrow()

        printBalances()

        nodeB.startFlow(CashIssueFlow(POUNDS(700),
                OpaqueBytes.of(0x01),
                notary
        )).getOrThrow()

        printBalances()

        val nodeAVaultupdate=nodeA.services.vaultService.updates.toFuture()
        val nodeBvaultupdate=nodeB.services.vaultService.updates.toFuture()

        print("Vault state clear ")

        nodeA.startFlow(ForeignExchangeFlow("Trade1",
                POUNDS(100).issuedBy(nodeB.info.singleIdentity().ref(0x01)),
                DOLLARS(200).issuedBy(nodeA.info.singleIdentity().ref(0x01)),
                nodeB.info.singleIdentity(),
                weAreBaseCurrencySeller = false
                )).getOrThrow()

        print("Node A start flow cleared")

        nodeAVaultupdate.get()

        val balancesA = nodeA.transaction {
            nodeA.services.getCashBalances()
        }
        nodeBvaultupdate.get()
        val balancesB = nodeB.transaction {
            nodeB.services.getCashBalances()
        }

        println("BalanceA\n$balancesA")
        println("BalanceB\n$balancesB")




    }



    private fun printBalances() {
        // Print out the balances
        nodeA.transaction {
            println("BalanceA\n" + nodeA.services.getCashBalances())
        }
        nodeB.transaction {
            println("BalanceB\n" + nodeB.services.getCashBalances())
        }
    }
}







