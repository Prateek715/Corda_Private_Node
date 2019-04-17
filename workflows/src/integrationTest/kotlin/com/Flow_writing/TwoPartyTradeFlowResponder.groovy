package com.Flow_writing

import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.finance.flows.TwoPartyTradeFlow

@InitiatedBy(com.Flow_writing.TwoPartyTradeFlow.class)
class TwoPartyTradeFlowResponder extends  FlowLogic<Void>{

    @Override
    Void call() throws FlowException {
        return null
    }
}
