package org.qortal.event;

import org.qortal.crosschain.Bitcoiny;

public class RequiredFeeUpdateEvent implements Event{
    private final Bitcoiny bitcoiny;

    public RequiredFeeUpdateEvent(Bitcoiny bitcoiny) {
        this.bitcoiny = bitcoiny;
    }

    public Bitcoiny getBitcoiny() {
        return bitcoiny;
    }
}
