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

        // Initiate transaction Builder
        TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

        // upload attachment via private method
        String path = System.getProperty("user.dir");
        System.out.println("Working Directory = " + path);

        //Change the path to "../test.zip" for passing the unit test.
        //because the unit test are in a different working directory than the running node.
        String zipPath = unitTest ? "../test.zip" : "../../../test.zip";

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
        /** Add attachment logic - END */
        transactionBuilder.addAttachment(attachmentHash);
        SignedTransaction signedTx = getServiceHub().signInitialTransaction(transactionBuilder);

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
