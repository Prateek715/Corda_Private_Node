package com.Flow_writing

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.OwnableState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.flows.TwoPartyTradeFlow
import java.util.*

@InitiatingFlow
@StartableByRPC
class TwoPartyTradeFlow(private val otherSideSession: FlowSession,
                      private val assetToSell: StateAndRef<OwnableState>,
                      private val price: Amount<Currency>,
                      private val myParty: PartyAndCertificate,
                      override val progressTracker: ProgressTracker = TwoPartyTradeFlow.Seller.tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            fun tracker() = ProgressTracker()
        }

        @Suspendable
        override fun call(): SignedTransaction {
            TODO()
        }
    }


