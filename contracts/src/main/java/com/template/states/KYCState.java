package com.template.states;

import com.template.contracts.KYCContract;
import com.template.models.KYCModel;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

import java.util.Arrays;
import java.util.List;
import net.corda.core.identity.Party;

// *********
// * State *
// *********
@BelongsToContract(KYCContract.class)
public class KYCState implements ContractState {

    //private variables
    private final KYCModel kyc;
    private final Party owner;
    private final Party requester;

    /* Constructor of your Corda state */
    public KYCState(KYCModel kyc, Party owner, Party requester) {
        this.kyc = kyc;
        this.owner = owner;
        this.requester = requester;
    }

    //getters
    public KYCModel getKYC() {
        return kyc;
    }

    public Party getOwner() {
        return owner;
    }

    public Party getRequester() {
        return requester;
    }

    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(owner, requester);
    }
}