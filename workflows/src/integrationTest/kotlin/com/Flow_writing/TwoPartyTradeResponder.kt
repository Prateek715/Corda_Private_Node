package com.Flow_writing

import net.bytebuddy.implementation.bind.MethodDelegationBinder
import net.corda.core.contracts.Amount
import net.corda.core.contracts.OwnableState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import org.apache.sshd.client.session.AbstractClientSession
import java.util.*

@InitiatedBy(TwoPartyTradeFlow::class)
class TwoPartyTradeResponder(private val sellerSession: FlowSession,
                             private val notary: Party,
                             private val acceptablePrice: Amount<Currency>,
                             private val typeToBuy: Class<out OwnableState>,
                             private val anonymous: Boolean) : FlowLogic<SignedTransaction>() {





    override fun call(): SignedTransaction {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}