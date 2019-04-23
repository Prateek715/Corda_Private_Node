package com.Flow_writing

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.utils.sumCashBy
import net.corda.finance.flows.TwoPartyTradeFlow
import java.util.*


@InitiatingFlow
@StartableByRPC
class TwoPartyTradeFlow(private val otherSideSession: FlowSession,
                      private val assetToSell: StateAndRef<OwnableState>,
                      private val price: Amount<Currency>,
                      private val myParty: PartyAndCertificate,
                      override val progressTracker: ProgressTracker = TwoPartyTradeFlow.Seller.tracker()) : FlowLogic<SignedTransaction>() {

    @CordaSerializable
    data class SellerTradeInfo(
            val price: Amount<Currency>,
            val payToIdentity: PartyAndCertificate
    )
        companion object {
        fun tracker() = ProgressTracker()
    }

        @Suspendable
        override fun call(): SignedTransaction {

            val hello = SellerTradeInfo(price, myParty)
            // What we get back from the other side is a transaction that *might* be valid and acceptable to us,
            // but we must check it out thoroughly before we sign!
            // SendTransactionFlow allows seller to access our data to resolve the transaction.
            subFlow(SendStateAndRefFlow(otherSideSession, listOf(assetToSell)))
            otherSideSession.send(hello)

            // Verify and sign the transaction.
            progressTracker.currentStep = TwoPartyTradeFlow.Seller.Companion.VERIFYING_AND_SIGNING

            // DOCSTART 07
            // Sync identities to ensure we know all of the identities involved in the transaction we're about to
            // be asked to sign
            subFlow(IdentitySyncFlow.Receive(otherSideSession))
            // DOCEND 07

            // DOCSTART 5
            val signTransactionFlow = object : SignTransactionFlow(otherSideSession, TwoPartyTradeFlow.Seller.Companion.VERIFYING_AND_SIGNING.childProgressTracker()) {
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


