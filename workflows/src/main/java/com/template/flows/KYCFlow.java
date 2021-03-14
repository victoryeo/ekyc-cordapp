package com.template.flows;

import com.template.models.KYCModel;
import com.template.states.KYCState;
import com.template.contracts.KYCContract;
import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Command;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.ServiceHub;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.flows.SignTransactionFlow;
import net.corda.core.transactions.SignedTransaction;
import static net.corda.core.contracts.ContractsDSL.requireThat;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.TransactionState;
import net.corda.core.crypto.SecureHash;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

// ******************
// * Initiator flow *
// ******************
@InitiatingFlow
@StartableByRPC
public class KYCFlow extends FlowLogic<Void> {
    private final String kycName;
    private final Party otherParty;

    // We will not use these ProgressTracker for this Hello-World sample
    private final ProgressTracker progressTracker = new ProgressTracker(
        CONSTRUCTING_KYC,
        VERIFYING,
        SIGNING,
        NOTARY,
        RECORDING,
        SENDING_FINAL_TRANSACTION
    );

    private static final ProgressTracker.Step CONSTRUCTING_KYC = new ProgressTracker.Step(
            "Constructing proposed kyc.");
    private static final ProgressTracker.Step VERIFYING = new ProgressTracker.Step(
            "Verifying signatures and contract constraints.");
    private static final ProgressTracker.Step SIGNING = new ProgressTracker.Step(
            "Signing transaction with our private key.");
    private static final ProgressTracker.Step NOTARY = new ProgressTracker.Step(
            "Obtaining notary signature.");
    private static final ProgressTracker.Step RECORDING = new ProgressTracker.Step(
            "Recording transaction in vault.");
    private static final ProgressTracker.Step SENDING_FINAL_TRANSACTION = new ProgressTracker.Step(
            "Sending fully signed transaction to other party.");

    public KYCFlow(String kycName, Party otherParty) {
        this.kycName = kycName;
        this.otherParty = otherParty;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    private boolean unitTest = false;

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    @Override
    public Void call() throws FlowException {
        // You retrieve the notary identity from the network map.
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        // Start eKYC process
        progressTracker.setCurrentStep(CONSTRUCTING_KYC);

        KYCModel kyc = new KYCModel();
        kyc.setKycId(1);
        kyc.setUserName(kycName);
        // build transaction
        KYCState outputState = new KYCState(kyc, getOurIdentity(), otherParty);
        List<PublicKey> requiredSigners = Arrays.asList(getOurIdentity().getOwningKey(), otherParty.getOwningKey());
        KYCContract.Commands.Issue commandData = new KYCContract.Commands.Issue();

        // Initiate transaction Builder
        TransactionBuilder transactionBuilder = new TransactionBuilder(notary);
        // add the components
        transactionBuilder.addCommand(commandData, getOurIdentity().getOwningKey(), otherParty.getOwningKey());
        transactionBuilder.addOutputState(outputState, KYCContract.ID);

        transactionBuilder.verify(getServiceHub());
        progressTracker.setCurrentStep(VERIFYING);

        // self signing
        SignedTransaction signedTx = getServiceHub().signInitialTransaction(transactionBuilder);
        progressTracker.setCurrentStep(SIGNING);

        // Creating a session with the other party.
        FlowSession otherPartySession = initiateFlow(otherParty);

        // Obtaining the counterparty's proof.
        //subFlow(new ReceiveTransactionFlow(otherPartySession));
        SignedTransaction fullySignedTx = subFlow(new SignTransactionFlow(otherPartySession) {
                @Override
                    protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    if (stx.getTx().getAttachments().isEmpty())
                        throw new FlowException("No Jar was being sent");
                }
        });
        //subFlow(new ReceiveFinalityFlow(otherPartySession, fullySignedTx.getId()));

        // Finalising the transaction.
        subFlow(new FinalityFlow(fullySignedTx, otherPartySession));
        progressTracker.setCurrentStep(SENDING_FINAL_TRANSACTION);

        return null;
    }

    private String uploadAttachment(String path, ServiceHub service, Party whoami, String filename) throws IOException {
        SecureHash attachmentHash = service.getAttachments().importAttachment(
                new FileInputStream(new File(path)),
                whoami.toString(),
                filename
        );

        return attachmentHash.toString();
    }
}
