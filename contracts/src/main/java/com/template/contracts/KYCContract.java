package com.template.contracts;

import com.template.states.KYCState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;
import java.util.List;

// ************
// * Contract *
// ************
// Add these imports:
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.identity.Party;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

// Replace TemplateContract's definition with:
public class KYCContract implements Contract {
    public static final String ID = "com.template.contracts.KYCContract";

    public static class Create implements CommandData {
    }

    public interface Commands extends CommandData {
        class Issue implements Commands {
        }
    }

    @Override
    public void verify(LedgerTransaction tx) {
        final CommandWithParties<KYCContract.Create> command = requireSingleCommand(tx.getCommands(), KYCContract.Create.class);

        // Constraints on the shape of the transaction.
        if (!tx.getInputs().isEmpty())
            throw new IllegalArgumentException("No inputs should be consumed when issuing an KYC request");
        if (!(tx.getOutputs().size() == 1))
            throw new IllegalArgumentException("There should be one output state of type KYCState.");

        // KYC-specific constraints.
        final KYCState output = tx.outputsOfType(KYCState.class).get(0);
        final Party owner = output.getOwner();
        final Party requester = output.getRequester();
        if (output.getKYC().getKycId() <= 0)
            throw new IllegalArgumentException("The KYC's value must be non-negative.");
        if (owner.equals(requester))
            throw new IllegalArgumentException("The owner and the requester cannot be the same entity.");

        // Constraints on the signers.
        final List<PublicKey> requiredSigners = command.getSigners();
        final List<PublicKey> expectedSigners = Arrays.asList(requester.getOwningKey(), owner.getOwningKey());
        if (requiredSigners.size() != 2)
            throw new IllegalArgumentException("There must be two signers.");
        if (!(requiredSigners.containsAll(expectedSigners)))
            throw new IllegalArgumentException("The requester and owner must be signers.");

    }
}