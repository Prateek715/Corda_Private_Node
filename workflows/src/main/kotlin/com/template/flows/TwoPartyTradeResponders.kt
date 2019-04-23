package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import net.bytebuddy.implementation.bind.MethodDelegationBinder
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.OwnableState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.finance.flows.TwoPartyTradeFlow
import net.corda.finance.workflows.asset.CashUtils
import java.util.*

@InitiatedBy(TwoPartyTrade::class)
class TwoPartyTradeResponders(private val sellerSession: FlowSession,
                              private val notary: Party,
                              private val acceptablePrice: Amount<Currency>,
                              private val typeToBuy:Class<out OwnableState>,
                              private val anonymous: Boolean):FlowLogic<SignedTransaction>() {


    companion object{
        fun tracker() = ProgressTracker()
    }

    override fun call(): SignedTransaction {

   progressTracker?.currentStep= TwoPartyTrade.Companion.RECEIVING

     val(assetForSale,tradeRequest) = receiveAndValidateTradeRequest()

        val buyerAnonymousIdentity = if (anonymous)
            serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)
        else
            ourIdentityAndCert
        // Put together a proposed transaction that performs the trade, and sign it.
        progressTracker?.currentStep = SignTransactionFlow.Companion.SIGNING
        val (ptx, cashSigningPubKeys) = assembleSharedTX(assetForSale, tradeRequest, buyerAnonymousIdentity)

        // DOCSTART 6
        // Now sign the transaction with whatever keys we need to move the cash.
        val partSignedTx = serviceHub.signInitialTransaction(ptx, cashSigningPubKeys)

        // Sync up confidential identities in the transaction with our counterparty
        subFlow(IdentitySyncFlow.Send(sellerSession, ptx.toWireTransaction(serviceHub)))

        // Send the signed transaction to the seller, who must then sign it themselves and commit
        // it to the ledger by sending it to the notary.
        progressTracker?.currentStep = net.corda.finance.flows.TwoPartyTradeFlow.Buyer.COLLECTING_SIGNATURES
        val sellerSignature = subFlow(CollectSignatureFlow(partSignedTx, sellerSession, sellerSession.counterparty.owningKey))
        val twiceSignedTx = partSignedTx + sellerSignature
        // DOCEND 6

        // Notarise and record the transaction.
        progressTracker?.currentStep = net.corda.finance.flows.TwoPartyTradeFlow.Buyer.RECORDING
        return subFlow(FinalityFlow(twiceSignedTx, sellerSession))

    }

    @Suspendable
    private fun receiveAndValidateTradeRequest(): Pair<StateAndRef<OwnableState>, TwoPartyTradeFlow.SellerTradeInfo>{
        val assetForSale =  subFlow(ReceiveStateAndRefFlow<OwnableState>(sellerSession)).single()
        return assetForSale to sellerSession.receive<TwoPartyTradeFlow.SellerTradeInfo>().unwrap {
            val asset = assetForSale.state.data
            val assetTypeName = asset.javaClass.name
            val assetForSaleIdentity = serviceHub.identityService.wellKnownPartyFromAnonymous(asset.owner)
            require(assetForSaleIdentity == sellerSession.counterparty){"Well known identity lookup returned identity that does not match counterparty"}

            // Register the identity we're about to send payment to. This shouldn't be the same as the asset owner
            // identity, so that anonymity is enforced.
            val wellKnownPayToIdentity = serviceHub.identityService.verifyAndRegisterIdentity(it.payToIdentity) ?: it.payToIdentity
            require(wellKnownPayToIdentity.party == sellerSession.counterparty) { "Well known identity to pay to must match counterparty identity" }

            if (it.price > acceptablePrice)
                throw net.corda.finance.flows.TwoPartyTradeFlow.UnacceptablePriceException(it.price)
            if (!typeToBuy.isInstance(asset))
                throw net.corda.finance.flows.TwoPartyTradeFlow.AssetMismatchException(typeToBuy.name, assetTypeName)

            it
        }
    }


    @Suspendable
    private fun assembleSharedTX(assetForsale : StateAndRef<OwnableState>, tradeRequest: TwoPartyTradeFlow.SellerTradeInfo,buyerAnonymousIdentity: PartyAndCertificate): net.corda.finance.flows.TwoPartyTradeFlow.Buyer.SharedTx{
        val ptx = TransactionBuilder(notary)

        // Add input and output states for the movement of cash, by using the Cash contract to generate the states
        val (tx, cashSigningPubKeys) = CashUtils.generateSpend(serviceHub, ptx, tradeRequest.price, ourIdentityAndCert, tradeRequest.payToIdentity.party)

        // Add inputs/outputs/a command for the movement of the asset.
        tx.addInputState(assetForsale)

        val (command, state) = assetForsale.state.data.withNewOwner(buyerAnonymousIdentity.party)
        tx.addOutputState(state, assetForsale.state.contract, assetForsale.state.notary)
        tx.addCommand(command, assetForsale.state.data.owner.owningKey)

        // We set the transaction's time-window: it may be that none of the contracts need this!
        // But it can't hurt to have one.
        val currentTime = serviceHub.clock.instant()
        tx.setTimeWindow(currentTime, 30.seconds)

        return net.corda.finance.flows.TwoPartyTradeFlow.Buyer.SharedTx(tx, cashSigningPubKeys)
    }
}
