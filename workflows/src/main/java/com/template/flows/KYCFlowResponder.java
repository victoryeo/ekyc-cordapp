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
import net.corda.core.flows.SendStateAndRefFlow;
import net.corda.confidential.IdentitySyncFlow;
import net.corda.core.crypto.SecureHash;
import net.corda.core.contracts.StateAndRef;

import static net.corda.core.contracts.ContractsDSL.requireThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

// ******************
// * Responder flow *
// ******************
@InitiatedBy(KYCFlow.class)
public class KYCFlowResponder extends FlowLogic<Void> {
    private final FlowSession otherPartySession;
    /*private final ProgressTracker progressTracker = new ProgressTracker(
        RECEIVING_KYC,
        SIGNING,
        SENDING_PROOF
    );
    private static final ProgressTracker.Step RECEIVING_KYC = new ProgressTracker.Step(
        "Receiving proposed kyc.");
    private static final ProgressTracker.Step SIGNING = new ProgressTracker.Step(
        "Signing proposed kyc.");
    private static final ProgressTracker.Step SENDING_PROOF = new ProgressTracker.Step(
        "Signing proof of kyc.");*/

    private final ProgressTracker progressTracker = new ProgressTracker();

    public KYCFlowResponder(FlowSession otherPartySession) {
        this.otherPartySession = otherPartySession;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    private boolean unitTest = false;

    @Suspendable
    @Override
    public Void call() throws FlowException {            
        String name = otherPartySession.receive(String.class).unwrap(it -> it);
              
        /** Add attachment logic - START */
        // upload attachment via private method
        String path = System.getProperty("user.dir");
        System.out.println("Working Directory = " + path);

        SecureHash attachmentHash = null;

        attachmentHash = SecureHash.randomSHA256();
        /** Add attachment logic - END */
        otherPartySession.send(attachmentHash.toString());
        

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
