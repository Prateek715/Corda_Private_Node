package com.template.flows

import net.corda.core.flows.*
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.util.*
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.*
import net.corda.finance.contracts.utils.sumCashBy


@InitiatingFlow
@StartableByRPC
class TwoPartyTrade(private val otherSideSession: FlowSession,
                    private val assetToSell:StateAndRef<OwnableState>,
                    private val price:Amount<Currency>,
                    private val myParty:PartyAndCertificate,
                    override val progressTracker : ProgressTracker = TwoPartyTrade.tracker() ) : FlowLogic<SignedTransaction>() {


    @CordaSerializable
    data class SellerTradeInfo(val price: Amount<Currency>,
                               val payToIdentity: PartyAndCertificate)


    companion object {
        object VERIFYING_AND_SIGNING : ProgressTracker.Step("Verifying and signing transaction proposal")
        object RECEIVING : ProgressTracker.Step("Waiting for seller trading info")

        object VERIFYING : ProgressTracker.Step("Verifying seller assets")
        object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal")
        object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from other parties") {
            override fun childProgressTracker() = SignTransactionFlow.tracker()
        }
        fun tracker() = ProgressTracker()
    }


    override fun call(): SignedTransaction {

        val hello= SellerTradeInfo(price,myParty)

        subFlow(SendStateAndRefFlow(otherSideSession, listOf(assetToSell)))
        otherSideSession.send(hello)

        progressTracker.currentStep = TwoPartyTrade.Companion.VERIFYING_AND_SIGNING

        subFlow(IdentitySyncFlow.Receive(otherSideSession))

        val signTransactionFlow = object : SignTransactionFlow(otherSideSession,TwoPartyTrade.tracker()){
            override fun checkTransaction(stx: SignedTransaction) {
                // Verify that we know who all the participants in the transaction are
                val states: Iterable<ContractState> = serviceHub.loadStates(stx.tx.inputs.toSet()).map { it.state.data } + stx.tx.outputs.map { it.data }
                states.forEach { state ->
                    state.participants.forEach { anon ->
                        require(serviceHub.identityService.wellKnownPartyFromAnonymous(anon) != null) {
                            "Transaction state $state involves unknown participant $anon"
                        }
                    }
                }

                if (stx.tx.outputStates.sumCashBy(myParty.party).withoutIssuer() != price)
                    throw FlowException("Transaction is not sending us the right amount of cash")
            }
        }

        val txId = subFlow(signTransactionFlow).id
        // DOCEND 5

        return subFlow(ReceiveFinalityFlow(otherSideSession, expectedTxId = txId))
    }

}
