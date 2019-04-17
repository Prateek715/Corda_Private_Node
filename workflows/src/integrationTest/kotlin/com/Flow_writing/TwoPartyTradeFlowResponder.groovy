package com.Flow_writing

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.flows.TwoPartyTradeFlow

@InitiatedBy(com.Flow_writing.TwoPartyTradeFlow.class)
class TwoPartyTradeFlowResponder extends  FlowLogic<SignedTransaction>{


    @Override
    SignedTransaction call() throws FlowException {
        return null
    }
}
