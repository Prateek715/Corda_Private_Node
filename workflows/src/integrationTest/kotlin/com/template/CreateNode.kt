package com.template
import com.template.Permissions.Companion.invokeRpc
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.h2.engine.User
import org.junit.Test
import com.template.Permissions.Companion.startFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import com.template.ALICE_NAME
import net.corda.core.utilities.getOrThrow
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.withoutIssuer
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.Vault
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.singleIdentity
import rx.Observable
import net.corda.core.messaging.FlowHandle
import net.corda.testing.core.expect
import net.corda.testing.core.expectEvents
import java.util.*
import kotlin.test.assertEquals
import net.corda.finance.flows.CashPaymentFlow;


class CreateNode {

    @Test
    fun aliceandbobexchange(){

       driver(DriverParameters(startNodesInProcess = true,cordappsForAllNodes = FINANCE_CORDAPPS)){
           val prateek= net.corda.testing.node.User("prateek","testpassword1",permissions = setOf(
            startFlow<CashIssueAndPaymentFlow>(),
                   invokeRpc("vaultTrackBy")
           ))



           val Prateektest= net.corda.testing.node.User("prateekTest","testpassord2",permissions = setOf(
                   startFlow<CashPaymentFlow>(),
                   invokeRpc("vaultTrackBy")
           ))


           val(alice,bob) = listOf(
                   startNode(providedName = ALICE_NAME,rpcUsers = listOf(prateek)),
                    startNode(providedName = BOB_NAME,rpcUsers = listOf(Prateektest))
           ).map { it.getOrThrow() }


           val  aliceClient = CordaRPCClient(alice.rpcAddress)

          println("AliceClient"+"##############################"+aliceClient);

           val aliceProxy : CordaRPCOps = aliceClient.start("prateek","testpassword1").proxy;

           println("AliceProxy"+"################################"+aliceProxy);


           val BobCkient = CordaRPCClient(bob.rpcAddress)

           val bobProxy :CordaRPCOps = BobCkient.start("prateekTest","testpassord2").proxy;

           println("@@@@@@@@@@@@@@@@@@@"+bobProxy);


           val bobVaultUpdates: Observable<Vault.Update<Cash.State>> = bobProxy.vaultTrackBy<Cash.State>().updates

            val aliceUpdates: Observable<Vault.Update<Cash.State>> = aliceProxy.vaultTrackBy<Cash.State>().updates


           println("%%%%%%%%%%%%%%%%%%%%"+bobVaultUpdates)

           println("^^^^^^^^^^^^^^^^^^^^"+aliceUpdates)

           val issueRef = OpaqueBytes.of(0)
          val alicehit= aliceProxy.startFlow(::CashIssueAndPaymentFlow,
                   1000.DOLLARS,
                   issueRef,
                   bob.nodeInfo.singleIdentity(),
                   true,
                   defaultNotaryIdentity
           ).returnValue.getOrThrow()

           println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"+alicehit)



           val gethit=bobVaultUpdates.expectEvents {
               expect { update ->
                   println("Bob got vault update of $update")
                   val amount: Amount<Issued<Currency>> = update.produced.first().state.data.amount
                   assertEquals(1000.DOLLARS, amount.withoutIssuer())
               }
           }

           println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$"+gethit)

           val getmeth=  bobProxy.startFlow(::CashPaymentFlow,
                   1000.DOLLARS,
                   alice.nodeInfo.singleIdentity()
           ).returnValue.getOrThrow()

           println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$"+getmeth)

           aliceUpdates.expectEvents {
               expect { update ->
                   println("Alice got vault update of $update")
                   val amount: Amount<Issued<Currency>> = update.produced.first().state.data.amount
                   assertEquals(1000.DOLLARS, amount.withoutIssuer())
               }
           }


       }























    }




}