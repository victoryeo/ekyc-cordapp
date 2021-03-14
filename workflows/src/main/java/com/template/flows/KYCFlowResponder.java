package com.template.flows;

import com.template.models.KYCModel;
import com.template.states.KYCState;
import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.ServiceHub   ;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.flows.SignTransactionFlow;;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.contracts.ContractState;
// Add this import:
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.confidential.IdentitySyncFlow;
import net.corda.core.crypto.SecureHash;

import static net.corda.core.contracts.ContractsDSL.requireThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

// ******************
// * Responder flow *
// ******************
@InitiatedBy(KYCFlow.class)
public class KYCFlowResponder extends FlowLogic<Void> {
    private final FlowSession otherPartySession;
    private final ProgressTracker progressTracker = new ProgressTracker(
        RECEIVING_KYC,
        SIGNING,
        SENDING_PROOF
    );
    private static final ProgressTracker.Step RECEIVING_KYC = new ProgressTracker.Step(
        "Receiving proposed kyc.");
    private static final ProgressTracker.Step SIGNING = new ProgressTracker.Step(
        "Signing proposed kyc.");
    private static final ProgressTracker.Step SENDING_PROOF = new ProgressTracker.Step(
        "Signing proof of kyc.");

    public KYCFlowResponder(FlowSession otherPartySession) {
        this.otherPartySession = otherPartySession;
    }

    private boolean unitTest = false;

    @Suspendable
    @Override
    public Void call() throws FlowException {
        /** Add attachment logic - START */
        // You retrieve the notary identity from the network map.
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        progressTracker.setCurrentStep(RECEIVING_KYC);

        // Initiate transaction Builder
        TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

        // upload attachment via private method
        String path = System.getProperty("user.dir");
        System.out.println("Working Directory = " + path);

        //Change the path to "../test.zip" for passing the unit test.
        //because the unit test are in a different working directory than the running node.
        String zipPath = unitTest ? "../../../test.zip" : "../../../idtest.txt";

        SecureHash attachmentHash = null;
        try {
            attachmentHash = SecureHash.parse(uploadAttachment(
                    zipPath,
                    getServiceHub(),
                    getOurIdentity(),
                    "testzip")
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        transactionBuilder.addAttachment(attachmentHash);
        /** Add attachment logic - END */

        progressTracker.setCurrentStep(SIGNING);
        SignedTransaction signedTx = getServiceHub().signInitialTransaction(transactionBuilder);

        // Sync up confidential info in the transaction with our counterparty
        //subFlow(new IdentitySyncFlow().Send(otherPartySession, transactionBuilder.toWireTransaction(getServiceHub())));
        subFlow(new SendTransactionFlow(otherPartySession, signedTx));
        progressTracker.setCurrentStep(SENDING_PROOF);

        class SignTxFlow extends SignTransactionFlow {
            private SignTxFlow(FlowSession otherPartySession, ProgressTracker progressTracker) {
                super(otherPartySession, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction stx) {
                requireThat(require -> {
                    ContractState output = stx.getTx().getOutputs().get(0).getData();
                    require.using("This must be an KYC transaction.", output instanceof KYCState);
                    KYCState kyc = (KYCState) output;
                    int kycId = kyc.getKYC().getKycId();
                    require.using("The KYC's ID is bigger than 0.", kycId > 0);
                    return null;
                });
            }
        }

        SecureHash expectedTxId = subFlow(new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker())).getId();

        subFlow(new ReceiveFinalityFlow(otherPartySession, expectedTxId));

        //subFlow(new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker()));

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
